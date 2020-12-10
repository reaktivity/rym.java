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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.bind.adapter.JsonbAdapter;

public final class RymDependenciesAdapter implements JsonbAdapter<List<RymDependency>, JsonArray>
{
    private static final String DEFAULT_GROUP_ID = "org.reaktivity";

    private static final String DEPENDENCY_FORMAT = "%s:%s:%s";
    private static final Pattern DEPENDENCY_PATTERN =
            Pattern.compile("(?:(?<groupId>[^:]+):)?(?<artifactId>[^:]+):(?<version>[^:]+)");

    @Override
    public JsonArray adaptToJson(
        List<RymDependency> dependencies) throws Exception
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (RymDependency dependency : dependencies)
        {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();

            String value = String.format(DEPENDENCY_FORMAT, groupId, artifactId, version);

            builder.add(value);
        }
        return builder.build();
    }

    @Override
    public List<RymDependency> adaptFromJson(
        JsonArray array) throws Exception
    {
        List<RymDependency> dependencies = null;

        if (array != null)
        {
            List<RymDependency> newDependencies = new ArrayList<>();
            array.forEach(e -> newDependencies.add(adaptEntryFromJson(e)));
            dependencies = newDependencies;
        }

        return dependencies;
    }

    private RymDependency adaptEntryFromJson(
        JsonValue value)
    {
        assert value.getValueType() == ValueType.STRING;

        final String entry = ((JsonString) value).getString();
        final Matcher matcher = DEPENDENCY_PATTERN.matcher(entry);

        RymDependency dependency = null;
        if (matcher.matches())
        {
            String groupId = Optional.ofNullable(matcher.group("groupId")).orElse(DEFAULT_GROUP_ID);
            String artifactId = matcher.group("artifactId");
            String version = matcher.group("version");

            dependency = new RymDependency(groupId, artifactId, version);
        }

        return dependency;
    }
}
