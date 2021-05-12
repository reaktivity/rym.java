/**
 * Copyright 2016-2021 The Reaktivity Project
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
import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.util.Collections.emptyList;
import static java.util.Collections.list;
import static java.util.Comparator.reverseOrder;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.github.rvesse.airline.annotations.Option;

@Command(
    name = "install",
    description = "Install dependencies")
public final class RymInstall extends RymCommand
{
    private static final String MODULE_INFO_JAVA_FILENAME = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILENAME = "module-info.class";

    @Option(name = { "--tidy" })
    public Boolean tidy = false;

    @Option(name = { "--debug" })
    public Boolean debug = false;

    @Option(name = { "--ignore-signing-information" })
    public Boolean ignoreSigning = false;

    @Override
    public void invoke()
    {
        int level = silent ? Message.MSG_WARN : Message.MSG_INFO;
        MessageLogger logger = new DefaultMessageLogger(level);
        Message.setDefaultLogger(logger);

        try
        {
            RymConfiguration config;

            Path rymFile = configDir.resolve("rym.json");

            logger.info(String.format("reading %s", rymFile));
            config = readOrDefaultConfig(rymFile);

            Path lockFile = lockDir.resolve("rym-lock.json");
            logger.info(String.format("reading %s", lockFile));
            config = overrideConfigIfLocked(config, rymFile, lockFile);

            logger.info("resolving dependencies");
            createDirectories(cacheDir);
            RymCache cache = new RymCache(config.repositories, cacheDir);
            Collection<RymArtifact> artifacts = cache.resolve(config.imports, config.dependencies);
            Map<RymDependency, RymDependency> resolvables = artifacts.stream()
                    .map(a -> a.id)
                    .collect(
                        toMap(
                            id -> RymDependency.of(id.group, id.artifact, null),
                            id -> RymDependency.of(id.group, id.artifact, id.version)));

            RymConfiguration resolved = new RymConfiguration();
            resolved.repositories = config.repositories;
            resolved.imports = null;
            resolved.dependencies = config.dependencies.stream()
                    .map(d -> ofNullable(resolvables.get(d)).orElse(d))
                    .collect(toList());

            if (!resolved.equals(config))
            {
                logger.info(String.format("writing %s", lockFile));
                writeLockFile(resolved, lockFile);
            }

            createDirectories(modulesDir);
            createDirectories(generatedDir);

            RymModule delegate = new RymModule();
            Collection<RymModule> modules = discoverModules(artifacts);
            migrateUnnamed(modules, delegate);
            generateSystemOnlyAutomatic(modules);
            delegateAutomatic(modules, delegate);
            copyNonDelegating(modules);

            if (!delegate.paths.isEmpty())
            {
                generateDelegate(delegate);
                generateDelegating(modules);
            }

            deleteDirectories(imageDir);
            linkModules(modules);
            logger.info("linked modules");

            generateLauncher();
            logger.info("generated launcher");

            if (tidy)
            {
                logger.info("tidying");
                deleteDirectories(modulesDir);
                deleteDirectories(generatedDir);
                deleteDirectories(cacheDir);
            }
        }
        catch (Exception ex)
        {
            logger.error(String.format("Error: %s", ex.getMessage()));
            throw new RuntimeException(ex);
        }
        finally
        {
            if (!silent)
            {
                logger.sumupProblems();
            }
        }
    }

    private RymConfiguration readOrDefaultConfig(
        Path rymFile) throws IOException
    {
        RymConfiguration config = new RymConfiguration();
        config.repositories = emptyList();
        config.imports = emptyList();
        config.dependencies = emptyList();

        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        if (Files.exists(rymFile))
        {
            try (InputStream in = newInputStream(rymFile))
            {
                config = builder.fromJson(in, RymConfiguration.class);
            }
        }

        return config;
    }

    private RymConfiguration overrideConfigIfLocked(
        RymConfiguration config,
        Path rymFile,
        Path lockFile) throws IOException
    {
        if (Files.exists(lockFile) &&
            getLastModifiedTime(lockFile).compareTo(getLastModifiedTime(rymFile)) >= 0)
        {
            Jsonb builder = JsonbBuilder.newBuilder()
                    .withConfig(new JsonbConfig().withFormatting(true))
                    .build();

            try (InputStream in = newInputStream(lockFile))
            {
                config = builder.fromJson(in, RymConfiguration.class);
            }
        }
        return config;
    }

    private void writeLockFile(
        RymConfiguration config,
        Path lockFile) throws IOException
    {
        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        createDirectories(lockDir);
        try (OutputStream out = newOutputStream(lockFile))
        {
            builder.toJson(config, out);
        }
    }

    private Collection<RymModule> discoverModules(
        Collection<RymArtifact> artifacts)
    {
        Path[] artifactPaths = artifacts.stream().map(a -> a.path).toArray(Path[]::new);
        Set<ModuleReference> references = ModuleFinder.of(artifactPaths).findAll();
        Map<URI, ModuleDescriptor> descriptors = references
                .stream()
                .filter(r -> r.location().isPresent())
                .collect(Collectors.toMap(r -> r.location().get(), r -> r.descriptor()));

        Collection<RymModule> modules = new LinkedHashSet<>();
        for (RymArtifact artifact : artifacts)
        {
            URI artifactURI = artifact.path.toUri();
            ModuleDescriptor descriptor = descriptors.get(artifactURI);
            RymModule module = descriptor != null ? new RymModule(descriptor, artifact) : new RymModule(artifact);
            modules.add(module);
        }

        return modules;
    }

    private void migrateUnnamed(
        Collection <RymModule> modules,
        RymModule delegate)
    {
        for (Iterator<RymModule> iterator = modules.iterator(); iterator.hasNext();)
        {
            RymModule module = iterator.next();
            if (module.name == null)
            {
                delegate.paths.addAll(module.paths);
                iterator.remove();
            }
        }

        assert !modules.stream().anyMatch(m -> m.name == null);
    }

    private void delegateAutomatic(
        Collection <RymModule> modules,
        RymModule delegate)
    {
        Map<RymArtifactId, RymModule> modulesMap = new LinkedHashMap<>();
        modules.forEach(m -> modulesMap.put(m.id, m));

        for (RymModule module : modules)
        {
            if (module.automatic)
            {
                delegateModule(delegate, module, modulesMap::get);
            }
        }

        assert !modules.stream().anyMatch(m -> m.automatic && !m.delegating);
    }

    private void delegateModule(
        RymModule delegate,
        RymModule module,
        Function<RymArtifactId, RymModule> lookup)
    {
        if (!module.delegating)
        {
            delegate.paths.addAll(module.paths);
            module.paths.clear();
            module.delegating = true;

            for (RymArtifactId dependId : module.depends)
            {
                RymModule depend = lookup.apply(dependId);
                delegateModule(delegate, depend, lookup);
            }
        }
    }

    private void generateSystemOnlyAutomatic(
        Collection<RymModule> modules) throws IOException
    {
        Map<RymModule, Path> promotions = new IdentityHashMap<>();

        for (RymModule module : modules)
        {
            if (module.automatic && module.depends.isEmpty())
            {
                Path generatedModulesDir = generatedDir.resolve("modules");
                Path generatedModuleDir = generatedModulesDir.resolve(module.name);

                deleteDirectories(generatedModuleDir);

                Files.createDirectories(generatedModuleDir);

                assert module.paths.size() == 1;
                Path artifactPath = module.paths.iterator().next();

                ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();
                jdeps.run(
                    System.out,
                    System.err,
                    "--generate-module-info", generatedModulesDir.toString(),
                    artifactPath.toString());

                Path generatedModuleInfo = generatedModuleDir.resolve(MODULE_INFO_JAVA_FILENAME);
                if (Files.exists(generatedModuleInfo))
                {
                    expandJar(generatedModuleDir, artifactPath);

                    ToolProvider javac = ToolProvider.findFirst("javac").get();
                    javac.run(
                            System.out,
                            System.err,
                            "-d", generatedModuleDir.toString(),
                            generatedModuleInfo.toString());

                    Path compiledModuleInfo = generatedModuleDir.resolve(MODULE_INFO_CLASS_FILENAME);
                    assert Files.exists(compiledModuleInfo);

                    Path generatedModulePath = generatedModulesDir.resolve(String.format("%s.jar", module.name));
                    JarEntry moduleInfoEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
                    moduleInfoEntry.setTime(318240000000L);
                    extendJar(artifactPath, generatedModulePath, moduleInfoEntry, compiledModuleInfo);

                    promotions.put(module, generatedModulePath);
                }
            }
        }

        for (Map.Entry<RymModule, Path> entry : promotions.entrySet())
        {
            RymModule module = entry.getKey();
            Path newArtifactPath = entry.getValue();

            ModuleDescriptor descriptor = moduleDescriptor(newArtifactPath);
            assert descriptor != null;

            RymArtifact newArtifact = new RymArtifact(module.id, newArtifactPath, module.depends);
            RymModule promotion = new RymModule(descriptor, newArtifact);

            modules.remove(module);
            modules.add(promotion);
        }
    }

    private void copyNonDelegating(
        Collection<RymModule> modules) throws IOException
    {
        for (RymModule module : modules)
        {
            if (!module.delegating)
            {
                assert module.paths.size() == 1;
                Path artifactPath = module.paths.iterator().next();
                Path modulePath = modulePath(module);
                Files.copy(artifactPath, modulePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void generateDelegate(
        RymModule delegate) throws IOException
    {
        Path generatedModulesDir = generatedDir.resolve("modules");
        Path generatedDelegateDir = generatedModulesDir.resolve(delegate.name);
        Files.createDirectories(generatedModulesDir);

        Path generatedDelegatePath = generatedModulesDir.resolve(String.format("%s.jar", delegate.name));
        try (JarOutputStream moduleJar = new JarOutputStream(Files.newOutputStream(generatedDelegatePath)))
        {
            Path moduleInfoPath = Paths.get(MODULE_INFO_CLASS_FILENAME);
            Path manifestPath = Paths.get("META-INF", "MANIFEST.MF");
            Path servicesPath = Paths.get("META-INF", "services");
            Path excludedPackage = Paths.get("org", "eclipse", "yasson", "internal", "components");
            String excludedClass = "BeanManagerInstanceCreator";
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
                        if (entryPath.equals(moduleInfoPath) ||
                            entryPath.equals(manifestPath) ||
                            (entryPath.startsWith(excludedPackage)) &&
                             entryPath.getFileName().toString().startsWith(excludedClass))
                        {
                            continue;
                        }

                        try (InputStream input = artifactJar.getInputStream(entry))
                        {
                            if (entryPath.startsWith(servicesPath) &&
                                entryPath.getNameCount() - servicesPath.getNameCount() == 1)
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
                String serviceName = service.getKey();
                Path servicePath = servicesPath.resolve(serviceName);
                String serviceImpl = service.getValue();

                JarEntry newEntry = new JarEntry(servicePath.toString());
                newEntry.setTime(318240000000L);
                moduleJar.putNextEntry(newEntry);
                moduleJar.write(serviceImpl.getBytes(UTF_8));
                moduleJar.closeEntry();
            }
        }

        ToolProvider jdeps = ToolProvider.findFirst("jdeps").get();
        jdeps.run(
            System.out,
            System.err,
            "--generate-module-info", generatedModulesDir.toString(),
            generatedDelegatePath.toString());

        Path generatedModuleInfo = generatedDelegateDir.resolve(MODULE_INFO_JAVA_FILENAME);
        assert Files.exists(generatedModuleInfo);

        String moduleInfoContents = Files.readString(generatedModuleInfo);
        Pattern pattern = Pattern.compile("(?:provides\\s+)([^\\s]+)(?:\\s+with)");
        Matcher matcher = pattern.matcher(moduleInfoContents);
        List<String> uses = new ArrayList<>();
        while (matcher.find())
        {
            String service = matcher.group(1);
            uses.add(String.format("uses %s;", service));
        }

        if (!uses.isEmpty())
        {
            Files.writeString(generatedModuleInfo,
                    moduleInfoContents.replace(
                            "}",
                            String.join("\n", uses) + "\n}"));
        }

        expandJar(generatedDelegateDir, generatedDelegatePath);

        ToolProvider javac = ToolProvider.findFirst("javac").get();
        javac.run(
                System.out,
                System.err,
                "-d", generatedDelegateDir.toString(),
                generatedModuleInfo.toString());

        Path compiledModuleInfo = generatedDelegateDir.resolve(MODULE_INFO_CLASS_FILENAME);
        assert Files.exists(compiledModuleInfo);

        Path delegatePath = modulePath(delegate);
        JarEntry moduleInfoEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
        moduleInfoEntry.setTime(318240000000L);
        extendJar(generatedDelegatePath, delegatePath, moduleInfoEntry, compiledModuleInfo);
    }

    private void generateDelegating(
        Collection<RymModule> modules) throws IOException
    {
        for (RymModule module : modules)
        {
            if (module.delegating)
            {
                Path generatedModulesDir = generatedDir.resolve("modules");
                Path generatedModuleDir = generatedModulesDir.resolve(module.name);
                Files.createDirectories(generatedModuleDir);

                Path generatedModuleInfo = generatedModuleDir.resolve(MODULE_INFO_JAVA_FILENAME);
                Files.write(generatedModuleInfo, Arrays.asList(
                        String.format("open module %s {", module.name),
                        String.format("    requires transitive %s;", RymModule.DELEGATE_NAME),
                        "}"));

                ToolProvider javac = ToolProvider.findFirst("javac").get();
                javac.run(
                        System.out,
                        System.err,
                        "-d", generatedModuleDir.toString(),
                        "--module-path", modulesDir.toString(),
                        generatedModuleInfo.toString());

                Path modulePath = modulePath(module);
                try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(modulePath)))
                {
                    JarEntry newEntry = new JarEntry(MODULE_INFO_CLASS_FILENAME);
                    newEntry.setTime(318240000000L);
                    jar.putNextEntry(newEntry);
                    jar.write(Files.readAllBytes(generatedModuleDir.resolve(MODULE_INFO_CLASS_FILENAME)));
                    jar.closeEntry();
                }
            }
        }
    }

    private void linkModules(
        Collection<RymModule> modules) throws IOException
    {
        ToolProvider jlink = ToolProvider.findFirst("jlink").get();

        List<String> extraModuleNames = new ArrayList<>();
        if (debug)
        {
            extraModuleNames.add("jdk.jdwp.agent");
        }

        Stream<String> moduleNames = Stream.concat(modules.stream().map(m -> m.name), extraModuleNames.stream());

        List<String> args = new ArrayList<>(Arrays.asList(
            "--module-path", modulesDir.toString(),
            "--output", imageDir.toString(),
            "--no-header-files",
            "--no-man-pages",
            "--compress", "2",
            "--add-modules", moduleNames.collect(Collectors.joining(","))));

        if (ignoreSigning)
        {
            args.add("--ignore-signing-information");
        }

        if (!debug)
        {
            args.add("--strip-debug");
        }

        if (!silent)
        {
            args.add("--verbose");
        }

        jlink.run(
            System.out,
            System.err,
            args.toArray(String[]::new));
    }

    private void generateLauncher() throws IOException
    {
        Path ryPath = launcherDir.resolve("ry");
        Files.write(ryPath, Arrays.asList(
                "#!/bin/sh",
                String.format(String.join(" ", Arrays.asList(
                    "%s/bin/java",
                    "--add-opens java.base/sun.nio.ch=org.agrona.core",
                    "$JAVA_OPTIONS",
                    "-m org.reaktivity.ry/org.reaktivity.ry.internal.RyMain \"$@\"")),
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

    private Path modulePath(
        RymModule module)
    {
        return modulesDir.resolve(String.format("%s.jar", module.name));
    }

    private void expandJar(
        Path targetDir,
        Path sourcePath) throws IOException
    {
        try (JarFile sourceJar = new JarFile(sourcePath.toFile()))
        {
            for (JarEntry entry : list(sourceJar.entries()))
            {
                Path entryPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory())
                {
                    createDirectories(entryPath);
                }
                else
                {
                    Path parentPath = entryPath.getParent();
                    if (!Files.exists(parentPath))
                    {
                        createDirectories(parentPath);
                    }

                    try (InputStream input = sourceJar.getInputStream(entry))
                    {
                        Files.write(entryPath, input.readAllBytes());
                    }
                }
            }
        }
    }

    private void extendJar(
        Path sourcePath,
        Path targetPath,
        JarEntry newEntry,
        Path newEntryPath) throws IOException
    {
        try (JarFile sourceJar = new JarFile(sourcePath.toFile());
             JarOutputStream targetJar = new JarOutputStream(Files.newOutputStream(targetPath)))
        {
            for (JarEntry entry : list(sourceJar.entries()))
            {
                targetJar.putNextEntry(entry);
                if (!entry.isDirectory())
                {
                    try (InputStream input = sourceJar.getInputStream(entry))
                    {
                        targetJar.write(input.readAllBytes());
                    }
                }
                targetJar.closeEntry();
            }

            targetJar.putNextEntry(newEntry);
            targetJar.write(Files.readAllBytes(newEntryPath));
            targetJar.closeEntry();
        }
    }

    private void deleteDirectories(
        Path dir) throws IOException
    {
        if (Files.exists(dir))
        {
            Files.walk(dir)
                 .sorted(reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
}
