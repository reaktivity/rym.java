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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

            Collection<RymArtifact> artifacts = resolveDependencies(config);
            logger.info("resolved dependencies");

            Map<String, RymModule> modules = new LinkedHashMap<>();
            for (RymArtifact artifact : artifacts)
            {
                String name = artifact.id.toString(); // TODO
                boolean automatic = false; // TODO
                if (name == null)
                {
                    RymModule unnamed = modules.computeIfAbsent("", n -> new RymModule());
                    unnamed.paths.add(artifact.path);
                }
                else
                {
                    RymModule module = new RymModule(name, artifact.path, automatic);
                    modules.put(name, module);
                }
            }

            //modules.values().forEach(System.out::println);

            copyModules(artifacts);
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

    private Collection<RymArtifact> resolveDependencies(
        RymConfiguration config) throws IOException
    {
        createDirectories(cacheDir);

        RymCache cache = new RymCache(config.repositories, cacheDir);

        cache.clean();

        Map<RymArtifactId, RymArtifact> artifacts = new LinkedHashMap<>();
        List<RymArtifactId> artifactIds = new LinkedList<>();
        for (RymDependency dependency : config.dependencies)
        {
            artifactIds.add(new RymArtifactId(dependency.groupId, dependency.artifactId, dependency.version));
        }

        while (!artifactIds.isEmpty())
        {
            RymArtifactId artifactId = artifactIds.remove(0);
            assert !artifacts.containsKey(artifactId);

            RymArtifact artifact = cache.resolve(artifactId);
            assert artifact != null;
            artifacts.put(artifactId, artifact);

            for (RymArtifactId depend : artifact.depends)
            {
                if (!artifacts.containsKey(depend) && !artifactIds.contains(depend))
                {
                    artifactIds.add(depend);
                }
            }
        }

        return artifacts.values();
    }

    private void copyModules(
        Collection<RymArtifact> artifacts) throws IOException
    {
        Files.createDirectories(modulesDir);
        for (RymArtifact artifact : artifacts)
        {
            String moduleName = String.format("%s.jar", artifact.id.artifact);
            Path target = modulesDir.resolve(moduleName);
            Files.copy(artifact.path, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
