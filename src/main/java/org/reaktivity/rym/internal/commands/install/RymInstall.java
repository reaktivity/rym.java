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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.spi.ToolProvider;
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

            RymModule delegate = new RymModule();
            Map<RymArtifactId, RymModule> modules = new LinkedHashMap<>();
            for (RymArtifact artifact : artifacts)
            {
                ModuleDescriptor descriptor = moduleDescriptor(artifact.path);

                if (descriptor == null)
                {
                    delegate.paths.add(artifact.path);
                    modules.put(artifact.id, delegate);
                }
                else
                {
                    RymModule module = new RymModule(descriptor, artifact);
                    if (module.automatic)
                    {
                        delegate.paths.addAll(module.paths);
                        modules.put(module.id, new RymModule(module));
                    }
                    else
                    {
                        modules.put(module.id, module);
                    }
                }
            }

            createDirectories(modulesDir);
            prepareDelegateModule(logger, delegate);
            prepareModules(logger, modules);
            linkModules(logger, modules.values());
            logger.info("prepared modules");

            generateLauncher();
            logger.info("generated launcher");
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

    private void prepareDelegateModule(
        MessageLogger logger,
        RymModule delegate) throws IOException
    {
        Path modulePath = modulesDir.resolve(String.format("%s.jar", delegate.name));
        try (JarOutputStream moduleJar = new JarOutputStream(Files.newOutputStream(modulePath)))
        {
            Path manifestPath = Paths.get("META-INF", "MANIFEST.MF");
            Path servicesPath = Paths.get("META-INF", "services");
            Set<String> entryNames = new HashSet<>();
            Map<String, String> services = new HashMap<>();
            for (Path path : delegate.paths)
            {
                try (JarFile artifactJar = new JarFile(path.toFile()))
                {
                    for (JarEntry entry : Collections.list(artifactJar.entries()))
                    {
                        String entryName = entry.getName();
                        Path entryPath = Paths.get(entryName);
                        if (entryPath.equals(manifestPath))
                        {
                            continue;
                        }

                        try (InputStream input = artifactJar.getInputStream(entry))
                        {
                            if (entryPath.startsWith(servicesPath) && entryPath.getNameCount() == servicesPath.getNameCount() + 1)
                            {
                                Path servicePath = servicesPath.relativize(entryPath);
                                assert servicePath.getNameCount() == 1;
                                String serviceName = servicePath.toString();
                                String serviceImpl = new String(input.readAllBytes(), UTF_8);
                                String existing = services.getOrDefault(serviceName, "");
                                services.put(serviceName, existing.concat(serviceImpl));
                            }
                            else if (entryNames.add(entryName))
                            {
                                moduleJar.putNextEntry(entry);
                                moduleJar.write(input.readAllBytes());
                                moduleJar.closeEntry();
                            }
                        }
                    }
                }
            }

            for (Map.Entry<String, String> service : services.entrySet())
            {
                JarEntry newEntry = new JarEntry(servicesPath.resolve(service.getKey()).toString());
                newEntry.setTime(318240000000L);
                moduleJar.putNextEntry(newEntry);
                moduleJar.write(service.getValue().getBytes(UTF_8));
                moduleJar.closeEntry();
            }
        }

        Path moduleGenDir = tempDir.resolve(String.format("%s-src", delegate.name));
        Path moduleDir = tempDir.resolve(delegate.name);
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();
        jdeps.run(
            System.out,
            System.err,
            "--generate-module-info", moduleGenDir.toString(),
            modulePath.toString());

        try (JarFile jar = new JarFile(modulePath.toFile()))
        {
            for (JarEntry entry : Collections.list(jar.entries()))
            {
                try (InputStream input = jar.getInputStream(entry))
                {
                    Path entryPath = moduleDir.resolve(entry.getName());
                    if (entry.isDirectory())
                    {
                        createDirectories(entryPath);
                    }
                    else
                    {
                        Files.write(entryPath, input.readAllBytes());
                    }
                }
            }
        }

        Path moduleInfo = moduleGenDir.resolve(delegate.name).resolve("module-info.java");
        ToolProvider javac = ToolProvider.findFirst("javac").get();
        OutputStream out = new ByteArrayOutputStream();
        int result = javac.run(
                new PrintStream(out),
                System.err,
                "-d", moduleDir.toString(),
                moduleInfo.toString());
        if (result != 0)
        {
            logger.error(out.toString());
        }

        Path moduleTempPath = modulePath.resolveSibling(String.format("%s.tmp.jar", delegate.name));
        Files.move(modulePath, moduleTempPath);
        try (JarFile tempJar = new JarFile(moduleTempPath.toFile());
             JarOutputStream moduleJar = new JarOutputStream(Files.newOutputStream(modulePath)))
        {
            for (JarEntry entry : Collections.list(tempJar.entries()))
            {
                moduleJar.putNextEntry(entry);
                try (InputStream input = tempJar.getInputStream(entry))
                {
                    moduleJar.write(input.readAllBytes());
                }
                moduleJar.closeEntry();
            }

            JarEntry newEntry = new JarEntry("module-info.class");
            newEntry.setTime(318240000000L);
            moduleJar.putNextEntry(newEntry);
            moduleJar.write(Files.readAllBytes(moduleDir.resolve("module-info.class")));
            moduleJar.closeEntry();
        }
        Files.delete(moduleTempPath);
    }

    private void prepareModules(
        MessageLogger logger,
        Map<RymArtifactId, RymModule> modules) throws IOException
    {
        for (RymModule module : modules.values())
        {
            Path modulePath = modulesDir.resolve(String.format("%s.jar", module.name));
            if (module.paths.isEmpty())
            {
                Path moduleGenDir = tempDir.resolve(String.format("%s-src", module.name));
                Path moduleDir = tempDir.resolve(module.name);
                Files.createDirectories(moduleGenDir);
                Path moduleInfo = moduleGenDir.resolve("module-info.java");
                Files.write(moduleInfo, Arrays.asList(
                        String.format("open module %s {", module.name),
                        String.format("    requires transitive %s;", RymModule.DELEGATE_NAME),
                        "}"));
                ToolProvider javac = ToolProvider.findFirst("javac").get();
                OutputStream out = new ByteArrayOutputStream();
                int result = javac.run(
                        new PrintStream(out),
                        System.err,
                        "-d", moduleDir.toString(),
                        "--module-path", modulesDir.toString(),
                        moduleInfo.toString());
                if (result != 0)
                {
                    logger.error(out.toString());
                }

                try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(modulePath)))
                {
                    JarEntry newEntry = new JarEntry("module-info.class");
                    newEntry.setTime(318240000000L);
                    jar.putNextEntry(newEntry);
                    jar.write(Files.readAllBytes(moduleDir.resolve("module-info.class")));
                    jar.closeEntry();
                }
            }
            else
            {
                assert module.paths.size() == 1;
                Path artifactPath = module.paths.iterator().next();
                Files.copy(artifactPath, modulePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void linkModules(
        MessageLogger logger,
        Collection<RymModule> modules) throws IOException
    {
        String javaHome = System.getProperty("java.home");
        ToolProvider jlink = ToolProvider.findFirst("jlink").get();
        jlink.run(
            System.out,
            System.err,
            "--module-path", String.format("%s:%s/jmods", modulesDir, javaHome),
            "--output", imageDir.toString(),
            "--no-header-files",
            "--no-man-pages",
            "--strip-debug",
            "--compress", "2",
            "--add-modules", modules.stream().map(m -> m.name).collect(Collectors.joining(",")));
    }

    private void generateLauncher() throws IOException
    {
        Path ryPath = launcherDir.resolve("ry");
        Files.write(ryPath, Arrays.asList(
                "#!/bin/sh",
                "JLINK_VM_OPTIONS=",
                String.format(
                    "%s/bin/java " +
                    "$JLINK_VM_OPTIONS " +
                    "-m org.reaktivity.ry/org.reaktivity.ry.internal.RyMain \"$@\"",
                    imageDir)));
        ryPath.toFile().setExecutable(true);
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
