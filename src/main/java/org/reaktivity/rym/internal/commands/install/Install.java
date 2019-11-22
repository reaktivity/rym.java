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
package org.reaktivity.rym.internal.commands.install;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public final class Install implements Runnable
{
    public static final String DEFAULT_DEPENDENCY_FILENAME = "ry.deps";
    public static final String DEFAULT_DEPENDENCY_LOCK_FILENAME = String.format("%s.lock", DEFAULT_DEPENDENCY_FILENAME);

    public static final String DEFAULT_GROUP_NAME = "org.reaktivity";

    public static final String DEPS_PROP_REPOSITORIES = "repositories";
    public static final String DEPS_PROP_DEPENDENCIES = "dependencies";

    private final List<String> repositories;

    private final Map<String, String> dependencies;

    private boolean readDepsFile()
    {
        System.out.format("Reading %s\n", DEFAULT_DEPENDENCY_FILENAME);
        JsonElement deps = null;
        try (BufferedReader br = new BufferedReader(new FileReader(DEFAULT_DEPENDENCY_FILENAME)))
        {
            deps = new JsonParser().parse(br);
        }
        catch (FileNotFoundException e)
        {
            // TODO Handle file not found
            System.out.format("Error, can't find %s\n", DEFAULT_DEPENDENCY_FILENAME);
            return false;
        }
        catch (IOException e)
        {
            // TODO Handle this error
            System.err.format("Error: Something went wrong reading %s", DEFAULT_DEPENDENCY_FILENAME);
            e.printStackTrace();
            return false;
        }
        catch (JsonSyntaxException e)
        {
            // TODO Handle issue with JSON syntax
            System.err.format("Error: %s is not in JSON format:", DEFAULT_DEPENDENCY_FILENAME);
            System.err.println(e.getCause().getMessage());
            return false;
        }
        // It's possible for a file to pass the parse phase, but still not be valid JSON. So do an explicit check.
        if (!deps.isJsonObject())
        {
            // TODO Handle file not found
            System.err.println(String.format("Error: %s is not in JSON format\n", DEFAULT_DEPENDENCY_FILENAME));
            return false;
        }

        JsonObject depsObj = (JsonObject)deps;

        JsonElement repositoriesEl = depsObj.get(DEPS_PROP_REPOSITORIES);
        if (!repositoriesEl.isJsonArray())
        {
            // TODO Handle file not found
            System.err.format("Error: %s is not an array of strings", DEPS_PROP_REPOSITORIES);
            return false;
        }
        JsonArray repositoriesArr = (JsonArray)repositoriesEl;
        for (int i = 0; i < repositoriesArr.size(); i++)
        {
            if (repositoriesArr.get(i).isJsonPrimitive())
            {
                repositories.add(repositoriesArr.get(i).getAsString());
            }
            else
            {
                repositories.add(repositoriesArr.get(i).toString());
            }
        }

        JsonElement dependenciesEl = depsObj.get(DEPS_PROP_DEPENDENCIES);
        if (!dependenciesEl.isJsonObject())
        {
            // TODO Handle file not found
            System.err.format("Error: %s is not a JSON object", DEPS_PROP_DEPENDENCIES);
            return false;
        }
        JsonObject dependenciesObj = (JsonObject)dependenciesEl;
        dependenciesObj.keySet().forEach(key ->
        {
            String artifact = key.indexOf(':') > 0 ? key : String.format("%s:%s", DEFAULT_GROUP_NAME, key);
            JsonElement versionEl = dependenciesObj.get(key);
            // TODO Process version here. e.g. Is it a range?
            if (versionEl.isJsonPrimitive())
            {
                dependencies.put(artifact, versionEl.getAsString());
            }
            else
            {
                dependencies.put(artifact, versionEl.toString());
            }
        });

        return true;
    }

    private void writeDepsLockFile()
    {
        System.out.format("Generating lock file %s\n", DEFAULT_DEPENDENCY_LOCK_FILENAME);
        JsonObject depsLockObj = new JsonObject();

        JsonArray repositoriesArr = new JsonArray();
        depsLockObj.add(DEPS_PROP_REPOSITORIES, repositoriesArr);
        repositories.forEach(r -> repositoriesArr.add(r));

        JsonObject dependenciesObj = new JsonObject();
        depsLockObj.add(DEPS_PROP_DEPENDENCIES, dependenciesObj);
        dependencies.forEach((dep, ver) ->
        {
            JsonElement versionEl = new JsonPrimitive(ver);
            dependenciesObj.add(dep, versionEl);
        });

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (BufferedWriter br = new BufferedWriter(new FileWriter(DEFAULT_DEPENDENCY_LOCK_FILENAME)))
        {
            br.write(gson.toJson(depsLockObj));
        }
        catch (IOException e)
        {
            // TODO Handle error
            e.printStackTrace();
        }

    }

    @Override
    public void run()
    {
        if (!readDepsFile())
        {
            return;
        }

        writeDepsLockFile();
    }

    public Install()
    {
        // utility class

        this.repositories = new ArrayList<String>();

        this.dependencies = new LinkedHashMap<String, String>();
    }
}
