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
package org.reaktivity.rym.internal.commands.install.cache;

import static java.util.Comparator.reverseOrder;
import static org.apache.ivy.util.filter.FilterHelper.getArtifactTypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.reaktivity.rym.internal.commands.install.RymDependency;
import org.reaktivity.rym.internal.commands.install.RymRepository;

public final class RymCache
{
    private final Path directory;
    private final Ivy ivy;
    private final ResolveOptions options;
    private final Map<ModuleRevisionId, RymArtifact> artifacts;

    public RymCache(
        List<RymRepository> repositories,
        Path directory)
    {
        ResolveOptions options = new ResolveOptions();
        options.setLog(ResolveOptions.LOG_DOWNLOAD_ONLY);
        options.setArtifactFilter(getArtifactTypeFilter("jar"));
        options.setConfs("master,runtime".split(","));
        options.setRefresh(true);
        options.setOutputReport(false);
        this.options = options;

        ChainResolver chain = new ChainResolver();
        chain.setName("default");
        repositories.stream().map(this::newResolver).forEach(chain::add);

        IvySettings ivySettings = new IvySettings();
        ivySettings.setDefaultCache(directory.toFile());
        ivySettings.addConfigured(chain);
        ivySettings.setDefaultResolver(chain.getName());

        this.directory = directory;
        this.ivy = Ivy.newInstance(ivySettings);

        this.artifacts = new LinkedHashMap<>();
    }

    public void clean() throws IOException
    {
        Files.walk(directory)
             .sorted(reverseOrder())
             .forEach(RymCache::deleteFile);
    }

    public RymArtifact resolve(
        RymArtifactId dependency)
    {
        return resolve(dependency.group, dependency.artifact, dependency.version);
    }

    public RymArtifact resolve(
        RymDependency dependency)
    {
        return resolve(dependency.groupId, dependency.artifactId, dependency.version);
    }

    private RymArtifact resolve(
        String groupId,
        String artifactId,
        String version)
    {
        ModuleRevisionId reference = ModuleRevisionId.newInstance(groupId, artifactId, version);

        return artifacts.computeIfAbsent(reference, this::resolve);
    }

    private RymArtifact resolve(
        ModuleRevisionId resolveId)
    {
        RymArtifact artifact = null;

        try
        {
            ResolveReport resolved = ivy.resolve(resolveId, options, false);
            if (resolved.hasError())
            {
                throw new Exception("Unable to resolve: " + resolveId);
            }

            ArtifactDownloadReport[] downloads = resolved.getAllArtifactsReports();

            Set<RymArtifactId> depends = new LinkedHashSet<>();
            for (ArtifactDownloadReport download : downloads)
            {
                ModuleRevisionId dependId = download.getArtifact().getModuleRevisionId();
                if (!resolveId.equals(dependId))
                {
                    download.getArtifact().getModuleRevisionId();
                    RymArtifactId depend = newArtifactId(dependId);
                    depends.add(depend);
                }
            }

            for (ArtifactDownloadReport download : downloads)
            {
                if (resolveId.equals(download.getArtifact().getModuleRevisionId()))
                {
                    RymArtifactId id = newArtifactId(resolveId);
                    Path local = download.getLocalFile().toPath();
                    artifact = new RymArtifact(id, local, depends);
                }
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }

        return artifact;
    }

    private RymArtifactId newArtifactId(
        ModuleRevisionId resolveId)
    {
        String groupId = resolveId.getOrganisation();
        String artifactId = resolveId.getName();
        String version = resolveId.getRevision();

        return new RymArtifactId(groupId, artifactId, version);
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

    private static void deleteFile(
        Path file)
    {
        try
        {
            Files.delete(file);
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
