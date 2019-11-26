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

import static org.apache.ivy.core.LogOptions.LOG_QUIET;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;

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

    // TODO Hardcoding maven repo dir... What if user has a custom location?
    // ${user.home} will be populated by Ivy at runtime
    public static final String MAVEN_CACHE = "${user.home}/.m2/repository";

    private final List<String> repositories;
    private final Map<String, String> dependencies;

    public RymInstall()
    {
        this.repositories = new ArrayList<String>();
        this.dependencies = new LinkedHashMap<String, String>();
    }

    @Override
    public void run()
    {
        System.out.format("Reading %s\n", DEPENDENCY_FILENAME);
        try
        {
            readDepsFile();
        }
        catch (FileNotFoundException e)
        {
            System.err.format("Error: Can't find %s\n", DEPENDENCY_FILENAME);
            return;
        }
        catch (IOException e)
        {
            System.err.format("Error reading %s:\n", DEPENDENCY_FILENAME);
            System.err.println(e.getMessage());
            return;
        }

        System.out.format("Generating lock file %s\n", DEPENDENCY_LOCK_FILENAME);
        try
        {
            writeDepsLockFile();
        }
        catch (IOException e)
        {
            System.err.format("Error writing %s:\n", DEPENDENCY_LOCK_FILENAME);
            System.err.println(e.getMessage());
            return;
        }

        try
        {
            resolveDependencies();
        }
        catch (ParseException e)
        {
            // TODO Should not occur because we are generating the settings file correctly.
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Handle this
            e.printStackTrace();
        }
    }

    private void readDepsFile() throws FileNotFoundException, IOException
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

        if (!repositories.contains(MAVEN_CENTRAL_REPOSITORY))
        {
            repositories.add(MAVEN_CENTRAL_REPOSITORY);
        }
        JsonArray repositoriesArr = new JsonArray();
        depsLockObj.add(PROPERTY_REPOSITORIES, repositoriesArr);
        repositories.forEach(r -> repositoriesArr.add(r));

        if (dependencies.size() > 0)
        {
            JsonObject dependenciesObj = new JsonObject();
            depsLockObj.add(PROPERTY_DEPENDENCIES, dependenciesObj);
            dependencies.forEach((dep, ver) ->
            {
                JsonElement versionEl = new JsonPrimitive(ver);
                dependenciesObj.add(dep, versionEl);
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
        contents.append("  <caches>\n");
        contents.append("    <cache name=\"mycache\"\n");
        contents.append(String.format("           basedir=\"%s\"\n", MAVEN_CACHE));
        contents.append("           ivyPattern=\"${m2-pattern}\"\n");
        contents.append("           artifactPattern=\"${m2-pattern}\">\n");
        contents.append("    </cache>\n");
        contents.append("  </caches>\n");
        contents.append("  <resolvers>\n");
        contents.append("    <chain name=\"default\">\n");
        contents.append("      <url name=\"blah\">\n");
        contents.append("        <ivy pattern=\"https://repo1.maven.org/maven2/[module]/[revision]/ivy-[revision].xml\" />\n");
        contents.append("        <artifact pattern=\"https://repo1.maven.org/maven2/[module]/[revision]/[artifact]-[revision].[ext]\" />");
        contents.append("      </url>\n");
//        contents.append("      <ibiblio name=\"central\" m2compatible=\"true\" cache=\"mycache\" />\n");
        contents.append("    </chain>\n");
        contents.append("  </resolvers>\n");
        contents.append("</ivysettings>\n");

        BufferedWriter out = new BufferedWriter(new FileWriter(ivySettingsFile));
        out.write(contents.toString());
        out.close();
        return ivySettingsFile;
    }

    private File createIvyFile() throws IOException
    {
        File ivyFile = File.createTempFile("ivy.xml", null);
        ivyFile.deleteOnExit();

        // TODO What to put for "organization" and "module" values here:
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
        contents.append("    <dependency org=\"commons-lang\" name=\"commons-lang\" rev=\"2.0\"/>\n");
        contents.append("    <dependency org=\"commons-cli\" name=\"commons-cli\" rev=\"1.2\"/>\n");
        contents.append("  </dependencies>\n");
        contents.append("</ivy-module>\n");

        BufferedWriter out = new BufferedWriter(new FileWriter(ivyFile));
        out.write(contents.toString());
        out.close();
        return ivyFile;
    }

    private void resolveDependencies() throws ParseException, IOException
    {
        IvySettings ivySettings = new IvySettings();
        org.apache.ivy.Ivy ivy = Ivy.newInstance(ivySettings);
        ivy.configure(createIvySettingsFile());

        ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setLog(LOG_QUIET); // TODO Pick logging option: Always, on download only, or never.
        resolveOptions.setOutputReport(false); // TODO Show the report? LOG_QUIET suppresses it, even if setOutputReport is true
        ResolveReport resolveReport = ivy.resolve(createIvyFile(), resolveOptions);

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

}
