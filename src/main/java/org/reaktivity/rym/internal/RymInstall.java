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

import static org.apache.ivy.plugins.namespace.Namespace.SYSTEM_NAMESPACE;
import static org.apache.ivy.plugins.resolver.IBiblioResolver.DEFAULT_M2_ROOT;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.text.ParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.install.InstallOptions;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.MessageLoggerEngine;
import org.apache.ivy.util.filter.FilterHelper;

import com.github.rvesse.airline.annotations.Command;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

@Command(name = "install",
    description = "Installs dependencies")
public final class RymInstall implements Runnable
{
    public static final String DEPENDENCY_FILENAME = "ry.deps";
    public static final String DEPENDENCY_LOCK_FILENAME = String.format("%s.lock", DEPENDENCY_FILENAME);

    public static final String DEFAULT_GROUP_NAME = "org.reaktivity";

    public static final String PROPERTY_REPOSITORIES = "repositories";
    public static final String PROPERTY_DEPENDENCIES = "dependencies";

    public static final String MAVEN_CENTRAL_REPOSITORY = "https://repo1.maven.org/maven2/";

    // ${user.home} will be populated by Ivy at runtime
    public static final String MAVEN_CACHE = String.format("%s/.m2/repository", System.getProperty("user.home"));

    private final Set<String> repositories;
    private final Map<String, String> dependencies;

    public RymInstall()
    {
        this.repositories = new LinkedHashSet<String>();
        this.dependencies = new LinkedHashMap<String, String>();
    }

    @Override
    public void run()
    {
        try
        {
            System.out.format("Reading dependencies %s\n", DEPENDENCY_FILENAME);
            readDepsFile();

            System.out.format("Generating lock file %s\n", DEPENDENCY_LOCK_FILENAME);
            writeDepsLockFile();

            System.out.println("Resolving dependencies");
            ResolveReport resolveReport = resolveDependencies();

            if (resolveReport.hasError())
            {
                List<String> problems = resolveReport.getAllProblemMessages();
                if (problems != null && !problems.isEmpty())
                {
                    StringBuffer errorMsgs = new StringBuffer();
                    for (String problem : problems)
                    {
                        errorMsgs.append(problem);
                        errorMsgs.append("\n");
                    }
                    System.err.println(errorMsgs);
                }
            }
            else
            {
                System.out.println("Dependencies were successfully resolved");
            }

        }
        catch (FileNotFoundException e)
        {
            System.err.format("Error: %s\n", e.getMessage());
        }
        catch (IOException e)
        {
            System.err.format("Error: %s\n", e.getMessage());
        }
        catch (ParseException e)
        {
            // TODO Should not occur because we are generating the settings file correctly.
            System.err.format("Error: %s\n", e.getMessage());
        }
    }

    private void readDepsFile() throws IOException
    {
        JsonElement deps = null;
        try (BufferedReader br = new BufferedReader(new FileReader(DEPENDENCY_FILENAME)))
        {
            deps = new JsonParser().parse(br);
        }
        // It's possible for a file to pass the parse phase, but still not be valid JSON. So do an explicit check.
        if (!deps.isJsonObject())
        {
            // TODO Handle file not found
            throw new JsonSyntaxException(String.format("%s is not in JSON format\n", DEPENDENCY_FILENAME));
        }

        JsonObject depsObj = (JsonObject)deps;

        JsonElement repositoriesEl = depsObj.get(PROPERTY_REPOSITORIES);
        if (repositoriesEl != null)
        {
            if (!repositoriesEl.isJsonArray())
            {
                throw new JsonSyntaxException(String.format("%s is not an array of strings", PROPERTY_REPOSITORIES));
            }
            JsonArray repositoriesArr = (JsonArray)repositoriesEl;
            for (int i = 0; i < repositoriesArr.size(); i++)
            {
                String repository = repositoriesArr.get(i).isJsonPrimitive() ?
                    repositoriesArr.get(i).getAsString() : repositoriesArr.get(i).toString();
                repositories.add(repository);
            }
        }

        JsonElement dependenciesEl = depsObj.get(PROPERTY_DEPENDENCIES);
        if (dependenciesEl != null)
        {
            if (!dependenciesEl.isJsonObject())
            {
                // TODO Handle file not found
                throw new JsonSyntaxException(String.format("%s is not a JSON object", PROPERTY_DEPENDENCIES));
            }
            JsonObject dependenciesObj = (JsonObject)dependenciesEl;
            dependenciesObj.keySet().forEach(key ->
            {
                String artifact = key.indexOf(':') > 0 ? key : String.format("%s:%s", DEFAULT_GROUP_NAME, key);
                JsonElement versionEl = dependenciesObj.get(key);
                // TODO Process version here. e.g. Is it a range?
                String version = versionEl.isJsonPrimitive() ? versionEl.getAsString() : versionEl.toString();
                dependencies.put(artifact, version);
            });
        }
    }

    private void writeDepsLockFile() throws IOException
    {
        JsonObject depsLockObj = new JsonObject();

        repositories.add(MAVEN_CENTRAL_REPOSITORY);

        JsonArray repositoriesArr = new JsonArray();
        depsLockObj.add(PROPERTY_REPOSITORIES, repositoriesArr);
        repositories.forEach(repositoriesArr::add);

        if (dependencies.size() > 0)
        {
            JsonObject dependenciesObj = new JsonObject();
            depsLockObj.add(PROPERTY_DEPENDENCIES, dependenciesObj);
            dependencies.forEach((dep, ver) ->
            {
                dependenciesObj.add(dep, new JsonPrimitive(ver));
            });
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (BufferedWriter br = new BufferedWriter(new FileWriter(DEPENDENCY_LOCK_FILENAME)))
        {
            br.write(gson.toJson(depsLockObj));
        }

    }

    private File createIvySettingsFile() throws IOException
    {
        File ivySettingsFile = File.createTempFile("ivy-settings.xml", null);
        ivySettingsFile.deleteOnExit();

        StringBuffer contents = new StringBuffer();
        contents.append("<ivysettings>\n");
        contents.append("  <settings defaultResolver=\"default\"/>\n");
        contents.append("  <property name=\"m2-pattern\"\n");
        contents.append("            value=\"[organisation]/[module]/[revision]/[module]-[revision](-[classifier]).[ext]\"\n");
        contents.append("            override=\"false\" />\n");
        contents.append(String.format("  <caches defaultCacheDir=\"%s\"\n", MAVEN_CACHE));
        contents.append("           ivyPattern=\"${m2-pattern}\"\n");
        contents.append("           artifactPattern=\"${m2-pattern} \">\n");
//        contents.append("  <caches>\n");
//        contents.append("    <cache name=\"mycache\"\n");
//        contents.append(String.format("           basedir=\"%s\"\n", MAVEN_CACHE));
//        contents.append("           ivyPattern=\"${m2-pattern}\"\n");
//        contents.append("           artifactPattern=\"${m2-pattern}\">\n");
//        contents.append("    </cache>\n");
        contents.append("  </caches>\n");
        contents.append("  <resolvers>\n");
        contents.append("    <chain name=\"default\">\n");
//        contents.append("      <ibiblio name=\"central\" m2compatible=\"true\" cache=\"mycache\" root=\"https://repo1.maven.org/maven2/\"/>\n");
        contents.append("      <ibiblio name=\"central\" m2compatible=\"true\" root=\"https://repo1.maven.org/maven2/\"/>\n");
        // TODO Change name "blah" to something correct
//        contents.append("      <url name=\"blah\" m2compatible=\"true\" cache=\"mycache\">\n");
//        contents.append("        <ivy pattern=\"https://repo1.maven.org/maven2/${m2-pattern}\" />\n");
//        contents.append("        <artifact pattern=\"https://repo1.maven.org/maven2/${m2-pattern}\" />");
//        contents.append("      </url>\n");
        contents.append("    </chain>\n");
        contents.append("  </resolvers>\n");
        contents.append("</ivysettings>\n");
//        System.out.println(contents.toString());

        BufferedWriter out = new BufferedWriter(new FileWriter(ivySettingsFile));
        out.write(contents.toString());
        out.close();
        return ivySettingsFile;
    }

    private File createIvyFile() throws IOException
    {
        File ivyFile = File.createTempFile("ivy.xml", null);
        ivyFile.deleteOnExit();

        // TODO What to put for "organization" and "module" values here (currently "reaktivity" is hardcoded):
        StringBuffer contents = new StringBuffer();
        contents.append("<ivy-module version=\"2.0\">\n");
        contents.append("  <info organisation=\"reaktivity\" module=\"reaktivity-deps\"/>\n");
        contents.append("  <dependencies>\n");
        for (Map.Entry<String, String> entry : dependencies.entrySet())
        {
            String[] dependencyComponents = entry.getKey().split(":");
            contents.append(String.format("    <dependency org=\"%s\" name=\"%s\" rev=\"%s\"/>\n",
                dependencyComponents[0],
                dependencyComponents[1],
                entry.getValue()));
        }
        contents.append("  </dependencies>\n");
        contents.append("</ivy-module>\n");
        System.out.format("\n%s\n", contents);

        BufferedWriter out = new BufferedWriter(new FileWriter(ivyFile));
        out.write(contents.toString());
        out.close();
        return ivyFile;
    }

    private void printIvy(Ivy ivy)
    {
        IvySettings ivySettings = ivy.getSettings();

        System.out.print("  modules [");
        String[] modules = ivy.listModules("org.reaktivity");
        System.out.format("%d]:\n", modules.length);
        for (String m: modules)
        {
            System.out.format("    m=%s\n", m);
        };

        System.out.println("  resolvers:");
        ivySettings.getResolvers().forEach(r ->
        {
            if ("default".equalsIgnoreCase(r.getName()))
            {
                ChainResolver resolver = (ChainResolver)r;
                System.out.format("    %s (%s)\n", resolver.getName(), resolver.getTypeName());
                System.out.println("    dump:");
                resolver.dumpSettings();
                System.out.format("post-dump\n");
            }
            else if ("central".equalsIgnoreCase(r.getName()))
            {
                IBiblioResolver resolver = (IBiblioResolver)r;
                System.out.format("    %s (%s)\n", resolver.getName(), resolver.getTypeName());
                System.out.println("    dump:");
                resolver.dumpSettings();
                System.out.format("post-dump\n");
            }
            else
            {
                System.out.format("    %s (unknown type)\n", r.getName());
            }
        });
        System.out.format("  Default resolver=%s\n", ivySettings.getDefaultResolver().getName());
    }

    private ResolveReport resolveDependencies() throws ParseException, IOException
    {
        Message.setDefaultLogger(new DefaultMessageLogger(Message.MSG_DEBUG));

        int flag = 1;

        if (flag == 1 || flag == 3)
        {
            IBiblioResolver central = new IBiblioResolver();
            central.setName("central");
            central.setM2compatible(true);
//            central.setNamespace(SYSTEM_NAMESPACE.getName());

            FileSystemResolver local = new FileSystemResolver();
            local.setName("local");
            local.setM2compatible(true);
            local.setLocal(true);
            local.addArtifactPattern(String.format("%s/%s", MAVEN_CACHE, central.getPattern()));

            IvySettings ivySettings = new IvySettings();
            ivySettings.setDefaultCache(new File(MAVEN_CACHE));
            ivySettings.setDefaultCacheIvyPattern(central.getPattern());
            ivySettings.setDefaultCacheArtifactPattern(central.getPattern());

            ivySettings.addConfigured(local);
            ivySettings.addConfigured(central);
//            ivySettings.setDefaultResolver("central");

            Ivy ivy = Ivy.newInstance(ivySettings);

            ModuleRevisionId reaktivity = ModuleRevisionId.newInstance("org.reaktivity", "reaktor", "0.86");

            final InstallOptions options = new InstallOptions();
            return ivy.install(reaktivity, "central", "local", options);
        }

        if (flag == 2 || flag == 3)
        {
            Ivy ivy2 = Ivy.newInstance();
            ivy2.getLoggerEngine().pushLogger(new DefaultMessageLogger(Message.MSG_DEBUG));
            ivy2.configure(createIvySettingsFile());

            IvyContext.getContext().setIvy(ivy2);
            System.out.println("\nivy2:");
            printIvy(ivy2);

            ResolveOptions resolveOptions = new ResolveOptions();
            // resolveOptions.setLog(LOG_QUIET); // TODO Pick logging option: Always, on download only, or never.
            // resolveOptions.setOutputReport(false); // TODO Show report? Note: LOG_QUIET suppresses it, even if setOutputReport is true
            return ivy2.resolve(createIvyFile(), resolveOptions);
        }

        return null;
    }

}
