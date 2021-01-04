/**
 * Copyright 2016-2020 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.rym.internal.commands.install;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.MessageLogger;
import org.reaktivity.rym.internal.RymCommand;
import org.reaktivity.rym.internal.commands.install.cache.RymArtifact;
import org.reaktivity.rym.internal.commands.install.cache.RymArtifactId;
import org.reaktivity.rym.internal.commands.install.cache.RymCache;
import org.reaktivity.rym.internal.commands.install.cache.RymModule;

import com.github.rvesse.airline.annotations.Command;

@Command(
    name = "install",
    description = "Install dependencies")
public final class RymInstall extends RymCommand
{
    @Override
    public void invoke()
    {
        int level = silent ? Message.MSG_WARN : Message.MSG_INFO;
        MessageLogger logger = new DefaultMessageLogger(level);
        Message.setDefaultLogger(logger);

        try
        {
            Path depsFile = configDir.resolve("ry.deps");
            logger.info(String.format("reading %s", depsFile));
            Jsonb builder = JsonbBuilder.newBuilder()
                                        .withConfig(new JsonbConfig().withFormatting(true))
                                        .build();
            RymConfiguration config = builder.fromJson(newInputStream(depsFile), RymConfiguration.class);

            Path lockFile = lockDir.resolve("ry.deps.lock");
            logger.info(String.format("updating %s", lockFile));
            createDirectories(lockDir);
            builder.toJson(config, newOutputStream(lockFile));

            logger.info("resolving dependencies");
            createDirectories(cacheDir);
            RymCache cache = new RymCache(config.repositories, cacheDir);
            Collection<RymArtifact> artifacts = cache.resolve(config.dependencies);

            RymModule unnamed = new RymModule();
            Map<RymArtifactId, RymModule> modules = new LinkedHashMap<>();
            for (RymArtifact artifact : artifacts)
            {
                ModuleDescriptor descriptor = moduleDescriptor(artifact.path);

                if (descriptor == null)
                {
                    unnamed.paths.add(artifact.path);
                    modules.put(artifact.id, unnamed);
                }
                else
                {
                    RymModule module = new RymModule(descriptor, artifact);
                    modules.put(artifact.id, module);
                }
            }

            for (RymModule module : modules.values())
            {
                for (RymArtifactId depend : module.depends)
                {
                    modules.get(depend).refers.add(module.id);
                }
            }

            Set<RymArtifactId> automatics = modules.values()
                    .stream()
                    .filter(m -> m.automatic)
                    .map(m -> m.id)
                    .collect(Collectors.toSet());

            for (RymArtifactId automaticId : automatics)
            {
                RymModule automatic = modules.get(automaticId);
                Set<RymArtifactId> refers = new LinkedHashSet<>();
                Deque<RymArtifactId> work = new LinkedList<>();
                automatic.refers.forEach(work::offer);
                while (!work.isEmpty())
                {
                    RymArtifactId id = work.poll();
                    if (refers.add(id))
                    {
                        RymModule module = modules.get(id);
                        module.refers.forEach(work::offer);
                    }
                }

                if (refers.stream().map(modules::get).anyMatch(m -> !m.automatic))
                {
                    unnamed.paths.addAll(automatic.paths);
                    modules.put(automatic.id, new RymModule(automatic));
                }
            }

            if (!unnamed.paths.isEmpty())
            {
                modules.put(unnamed.id, unnamed);
            }

            for (RymModule module : modules.values())
            {
                System.out.format("%s\n", module.name);
                for (Path path : module.paths)
                {
                    System.out.format("  [%s]\n", path);
                }
            }

            copyModules(modules.values());
            logger.info("prepared modules");
        }
        catch (Exception ex)
        {
            logger.error(String.format("Error: %s", ex.getMessage()));
        }
        finally
        {
            if (!silent)
            {
                logger.sumupProblems();
            }
        }
    }

    private void copyModules(
        Collection<RymModule> modules) throws IOException
    {
        Files.createDirectories(modulesDir);
        for (RymModule module : modules)
        {
            String moduleName = String.format("%s.jar", module.name);
            Path target = modulesDir.resolve(moduleName);
            // TODO merge multiple paths
            if (!module.paths.isEmpty())
            {
                Files.copy(module.paths.iterator().next(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private ModuleDescriptor moduleDescriptor(
        Path archive)
    {
        ModuleDescriptor module = null;
        Set<ModuleReference> moduleRefs = ModuleFinder.of(archive).findAll();
        if (!moduleRefs.isEmpty())
        {
            module = moduleRefs.iterator().next().descriptor();
        }
        return module;
    }
}
