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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.bind.adapter.JsonbAdapter;

public final class RymDependenciesAdapter implements JsonbAdapter<List<RymDependency>, JsonObject>
{
    private static final String DEFAULT_GROUP_ID = "org.reaktivity";

    @Override
    public JsonObject adaptToJson(
        List<RymDependency> dependencies) throws Exception
    {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (RymDependency dependency : dependencies)
        {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();

            String key = String.format("%s:%s", groupId, artifactId);
            String value = version;

            builder.add(key, value);
        }
        return builder.build();
    }

    @Override
    public List<RymDependency> adaptFromJson(
        JsonObject obj) throws Exception
    {
        List<RymDependency> dependencies = null;

        if (obj != null)
        {
            List<RymDependency> newDependencies = new ArrayList<>();
            obj.asJsonObject().forEach((k, v) -> newDependencies.add(adaptEntryFromJson(k, v)));
            dependencies = newDependencies;
        }

        return dependencies;
    }

    private RymDependency adaptEntryFromJson(
        String key,
        JsonValue value)
    {
        assert value.getValueType() == ValueType.STRING;

        int colonAt = key.indexOf(':');
        String groupId = colonAt != -1 ? key.substring(0, colonAt) : DEFAULT_GROUP_ID;
        String artifactId = key.substring(colonAt + 1);
        String version = ((JsonString) value).getString();

        return new RymDependency(groupId, artifactId, version);
    }
}
