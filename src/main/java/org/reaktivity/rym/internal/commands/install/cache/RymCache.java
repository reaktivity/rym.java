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

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static org.apache.ivy.util.filter.FilterHelper.getArtifactTypeFilter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorBuilder;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.reaktivity.rym.internal.commands.install.RymDependency;
import org.reaktivity.rym.internal.commands.install.RymRepository;

public final class RymCache
{
    private final Ivy ivy;
    private final ResolveOptions options;

    public RymCache(
        List<RymRepository> repositories,
        Path directory)
    {
        ResolveOptions options = new ResolveOptions();
        options.setLog(ResolveOptions.LOG_DOWNLOAD_ONLY);
        options.setArtifactFilter(getArtifactTypeFilter(new String[]{"jar", "bundle"}));
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

        this.ivy = Ivy.newInstance(ivySettings);
    }

    public List<RymArtifact> resolve(
        List<RymDependency> imports,
        List<RymDependency> dependencies)
    {
        Map<RymDependency, String> imported = resolveImports(imports);
        ModuleDescriptor resolvable = createResolvableDescriptor(imported, dependencies);

        return resolveDependencyArtifacts(resolvable);
    }

    private Map<RymDependency, String> resolveImports(
        List<RymDependency> imports)
    {
        Map<RymDependency, String> imported = new LinkedHashMap<>();

        ModuleDescriptor resolvable = createResolvableDescriptor(emptyMap(), imports);
        List<ModuleDescriptor> importedIds = resolveDependencyDescriptors(resolvable);
        importedIds.stream()
            .map(PomModuleDescriptorBuilder::getDependencyManagementMap)
            .flatMap(m -> m.entrySet().stream())
            .forEach(e -> imported.put(asDependency(e.getKey()), e.getValue()));

        return imported;
    }

    private RymDependency asDependency(
        ModuleId moduleId)
    {
        return RymDependency.of(moduleId.getOrganisation(), moduleId.getName(), null);
    }

    private DefaultModuleDescriptor createResolvableDescriptor(
        Map<RymDependency, String> imported,
        List<RymDependency> dependencies)
    {
        List<ModuleRevisionId> revisionIds = dependencies.stream()
            .map(d -> ModuleRevisionId.newInstance(d.groupId, d.artifactId, ofNullable(d.version).orElse(imported.get(d))))
            .collect(Collectors.toList());

        ModuleRevisionId[] resolveIds = revisionIds.toArray(new ModuleRevisionId[0]);

        boolean changing = false;
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(
                ModuleRevisionId.newInstance("caller", "all-caller", "working"), "integration", null, true);
        for (String conf : options.getConfs())
        {
            moduleDescriptor.addConfiguration(new Configuration(conf));
        }
        moduleDescriptor.setLastModified(System.currentTimeMillis());
        for (ModuleRevisionId mrid : resolveIds)
        {
            DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(moduleDescriptor, mrid,
                    true, changing, options.isTransitive());
            for (String conf : options.getConfs())
            {
                dd.addDependencyConfiguration(conf, conf);
            }
            moduleDescriptor.addDependency(dd);
        }
        return moduleDescriptor;
    }

    private List<ModuleDescriptor> resolveDependencyDescriptors(
        ModuleDescriptor moduleDescriptor)
    {
        List<ModuleDescriptor> descriptors = new LinkedList<>();
        try
        {
            ResolveReport report = ivy.resolve(moduleDescriptor, options);
            if (report.hasError())
            {
                throw new Exception("Unable to resolve: " + report);
            }

            for (IvyNode node : report.getDependencies())
            {
                ModuleDescriptor descriptor = node.getDescriptor();
                if (descriptor == null)
                {
                    continue;
                }
                descriptors.add(descriptor);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }

        return descriptors;
    }

    private List<RymArtifact> resolveDependencyArtifacts(
        ModuleDescriptor moduleDescriptor)
    {
        List<RymArtifact> artifacts = new LinkedList<>();
        try
        {
            ResolveReport report = ivy.resolve(moduleDescriptor, options);
            if (report.hasError())
            {
                throw new Exception("Unable to resolve: " + report);
            }

            for (IvyNode node : report.getDependencies())
            {
                ModuleDescriptor descriptor = node.getDescriptor();
                if (descriptor == null)
                {
                    continue;
                }

                ModuleRevisionId resolveId = descriptor.getModuleRevisionId();
                ArtifactDownloadReport[] downloads = report.getArtifactsReports(resolveId);
                if (downloads.length == 0)
                {
                    continue;
                }

                Set<RymArtifactId> depends = new LinkedHashSet<>();
                for (DependencyDescriptor dd : descriptor.getDependencies())
                {
                    ModuleRevisionId dependId = dd.getDependencyRevisionId();
                    ArtifactDownloadReport[] dependDownloads = report.getArtifactsReports(dependId);
                    if (dependDownloads.length != 0)
                    {
                        RymArtifactId depend = newArtifactId(dependId);
                        depends.add(depend);
                    }
                }

                RymArtifactId id = newArtifactId(resolveId);
                Path local = downloads[0].getLocalFile().toPath();
                RymArtifact artifact = new RymArtifact(id, local, depends);
                artifacts.add(artifact);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }

        return artifacts;
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
}
