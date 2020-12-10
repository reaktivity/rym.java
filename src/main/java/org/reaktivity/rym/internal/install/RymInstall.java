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
package org.reaktivity.rym.internal.install;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static org.apache.ivy.util.filter.FilterHelper.getArtifactTypeFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.MessageLogger;
import org.reaktivity.rym.internal.RymCommand;

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

        ResolveOptions options = new ResolveOptions();
        options.setLog(ResolveOptions.LOG_DOWNLOAD_ONLY);
        options.setArtifactFilter(getArtifactTypeFilter("jar"));
        options.setConfs("master,compile".split(","));
        options.setRefresh(true);
        options.setOutputReport(false);

        try
        {
            Path depsFile = configDir.resolve("ry.deps");
            logger.info(String.format("reading %s", depsFile));
            Jsonb builder = JsonbBuilder.create();
            RymConfiguration config = builder.fromJson(newInputStream(depsFile), RymConfiguration.class);

            Path lockFile = lockDir.resolve("ry.deps.lock");
            logger.info(String.format("updating %s", lockFile));
            createDirectories(lockDir);
            builder.toJson(config, newOutputStream(lockFile));

            createDirectories(cacheDir);
            boolean resolved = resolveDependencies(config, options);
            if (resolved)
            {
                logger.info("resolved dependencies");
            }
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

    private RepositoryResolver newResolver(
        RymRepository repository)
    {
        String name = "maven"; // TODO
        String root = repository.location;

        IBiblioResolver resolver = new IBiblioResolver();
        resolver.setName(name);
        resolver.setRoot(root);
        resolver.setM2compatible(true);

        return resolver;
    }

    private boolean resolveDependencies(
        RymConfiguration config,
        ResolveOptions options) throws ParseException, IOException
    {
        ChainResolver chain = new ChainResolver();
        chain.setName("default");

        config.getRepositories().stream().map(this::newResolver).forEach(chain::add);

        IBiblioResolver central = new IBiblioResolver();
        central.setName("central");
        central.setM2compatible(true);
        chain.add(central);

        IvySettings ivySettings = new IvySettings();
        ivySettings.setDefaultCache(cacheDir.toFile());
        ivySettings.addConfigured(chain);
        ivySettings.setDefaultResolver(chain.getName());

        Ivy ivy = Ivy.newInstance(ivySettings);

        boolean hasErrors = false;
        for (RymDependency dependency : config.getDependencies())
        {
            String groupId = dependency.groupId;
            String artifactId = dependency.artifactId;
            String version = dependency.version;

            ModuleRevisionId reference = ModuleRevisionId.newInstance(groupId, artifactId, version);

            ResolveReport report = ivy.resolve(reference, options, false);
            hasErrors |= report.hasError();
        }
        return !hasErrors;
    }
}
