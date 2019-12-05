/**
 * Copyright 2016-2019 The Reaktivity Project
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
package org.reaktivity.rym.internal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

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

import com.github.rvesse.airline.annotations.Command;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

@Command(
    name = "install",
    description = "Installs dependencies")
public final class RymInstall implements Runnable
{
    private static final String DEPENDENCY_FILENAME = "ry.deps";
    private static final String DEPENDENCY_LOCK_FILENAME = String.format("%s.lock", DEPENDENCY_FILENAME);

    private static final String DEFAULT_GROUP_ID = "org.reaktivity";

    private static final String PROPERTY_REPOSITORIES = "repositories";
    private static final String PROPERTY_DEPENDENCIES = "dependencies";

    private final Map<String, String> repositories;
    private final Map<String, String> dependencies;

    public RymInstall()
    {
        this.repositories = new LinkedHashMap<>();
        this.dependencies = new LinkedHashMap<>();
    }

    @Override
    public void run()
    {
        DefaultMessageLogger logger = new DefaultMessageLogger(Message.MSG_INFO);
        Message.setDefaultLogger(logger);

        ResolveOptions options = new ResolveOptions();
        options.setLog(ResolveOptions.LOG_DOWNLOAD_ONLY);

        try
        {
            logger.info(String.format("Reading dependencies: %s", DEPENDENCY_FILENAME));
            readDepsFile();

            logger.info(String.format("Updating lock file:   %s", DEPENDENCY_LOCK_FILENAME));
            writeDepsLockFile();

            logger.info("Resolving dependencies");
            boolean resolved = resolveDependencies(options);
            if (resolved)
            {
                logger.info("Dependencies were successfully resolved");
            }
            else
            {
                logger.error("Dependencies failed to resolve");
            }
        }
        catch (Exception ex)
        {
            logger.error(String.format("Error: %s", ex.getMessage()));
        }
        finally
        {
            logger.sumupProblems();
        }
    }

    private void readDepsFile() throws IOException
    {
        JsonElement deps = null;
        try (BufferedReader br = new BufferedReader(new FileReader(DEPENDENCY_FILENAME)))
        {
            deps = new JsonParser().parse(br);
        }
        if (!deps.isJsonObject())
        {
            throw new JsonSyntaxException(String.format("%s is not in JSON format\n", DEPENDENCY_FILENAME));
        }

        JsonObject depsObj = (JsonObject)deps;

        JsonElement repositoriesEl = depsObj.get(PROPERTY_REPOSITORIES);
        if (repositoriesEl != null)
        {
            if (!repositoriesEl.isJsonObject())
            {
                throw new JsonSyntaxException(String.format("%s is not a JSON object", PROPERTY_REPOSITORIES));
            }
            JsonObject repositoriesObj = (JsonObject)repositoriesEl;
            repositoriesObj.entrySet().forEach(e ->
            {
                if (e.getValue().isJsonPrimitive())
                {
                    repositories.put(e.getKey(), e.getValue().getAsString());
                }
                else
                {
                    throw new JsonSyntaxException(
                        String.format("The value of %s.%s is not a string", PROPERTY_REPOSITORIES, e.getKey()));
                }
            });
        }

        JsonElement dependenciesEl = depsObj.get(PROPERTY_DEPENDENCIES);
        if (dependenciesEl != null)
        {
            if (!dependenciesEl.isJsonObject())
            {
                throw new JsonSyntaxException(String.format("%s is not a JSON object", PROPERTY_DEPENDENCIES));
            }
            JsonObject dependenciesObj = (JsonObject)dependenciesEl;
            dependenciesObj.entrySet().forEach(e -> dependencies.put(e.getKey(), e.getValue().getAsString()));
        }
    }

    private void writeDepsLockFile() throws IOException
    {
        JsonObject depsLockObj = new JsonObject();

        JsonObject repositoriesObj = new JsonObject();
        depsLockObj.add(PROPERTY_REPOSITORIES, repositoriesObj);
        repositories.forEach((n, u) -> repositoriesObj.add(n, new JsonPrimitive(u)));

        if (!dependencies.isEmpty())
        {
            JsonObject dependenciesObj = new JsonObject();
            depsLockObj.add(PROPERTY_DEPENDENCIES, dependenciesObj);
            dependencies.forEach((dep, ver) -> dependenciesObj.add(dep, new JsonPrimitive(ver)));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (BufferedWriter br = new BufferedWriter(new FileWriter(DEPENDENCY_LOCK_FILENAME)))
        {
            br.write(gson.toJson(depsLockObj));
        }

    }

    private RepositoryResolver newResolver(
        Map.Entry<String, String> rootByName)
    {
        String name = rootByName.getKey();
        String root = rootByName.getValue();

        IBiblioResolver resolver = new IBiblioResolver();
        resolver.setName(name);
        resolver.setRoot(root);
        resolver.setM2compatible(true);

        return resolver;
    }

    private boolean resolveDependencies(
        ResolveOptions options) throws ParseException, IOException
    {
        ChainResolver chain = new ChainResolver();
        chain.setName("default");

        repositories.entrySet().stream().map(this::newResolver).forEach(chain::add);

        IvySettings ivySettings = new IvySettings();
        ivySettings.addConfigured(chain);
        ivySettings.setDefaultResolver(chain.getName());

        Ivy ivy = Ivy.newInstance(ivySettings);

        boolean hasErrors = false;
        for (Map.Entry<String, String> dependency : dependencies.entrySet())
        {
            String optionallyQualifiedArtifact = dependency.getKey();
            String version = dependency.getValue();

            String[] parts = optionallyQualifiedArtifact.split(":");
            String groupId = parts.length == 2 ? parts[0] : DEFAULT_GROUP_ID;
            String artifactId = parts[parts.length - 1];

            ModuleRevisionId reference = ModuleRevisionId.newInstance(groupId, artifactId, version);

            ResolveReport report = ivy.resolve(reference, options, false);
            hasErrors |= report.hasError();
        }
        return !hasErrors;
    }
}
