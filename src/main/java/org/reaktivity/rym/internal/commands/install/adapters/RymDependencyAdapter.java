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
package org.reaktivity.rym.internal.commands.install.adapters;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonString;
import javax.json.bind.adapter.JsonbAdapter;

import org.reaktivity.rym.internal.commands.install.RymDependency;

public final class RymDependencyAdapter implements JsonbAdapter<RymDependency, JsonString>
{
    private static final String DEFAULT_GROUP_ID = "org.reaktivity";

    private static final String DEPENDENCY_FORMAT = "%s:%s:%s";
    private static final Pattern DEPENDENCY_PATTERN =
            Pattern.compile("(?<groupId>[^:]+):(?<artifactId>[^:]+)(?::(?<version>[^:]+))?");

    @Override
    public JsonString adaptToJson(
        RymDependency dependency)
    {
        String groupId = dependency.groupId;
        String artifactId = dependency.artifactId;
        String version = dependency.version;

        String value = String.format(DEPENDENCY_FORMAT, groupId, artifactId, version);

        return Json.createValue(value);
    }

    @Override
    public RymDependency adaptFromJson(
        JsonString value)
    {
        final String entry = ((JsonString) value).getString();
        final Matcher matcher = DEPENDENCY_PATTERN.matcher(entry);

        RymDependency dependency = null;
        if (matcher.matches())
        {
            dependency = new RymDependency();
            dependency.groupId = Optional.ofNullable(matcher.group("groupId")).orElse(DEFAULT_GROUP_ID);
            dependency.artifactId = matcher.group("artifactId");
            dependency.version = matcher.group("version");
        }

        return dependency;
    }
}
