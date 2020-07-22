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
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

public final class RymRepositoriesAdapter implements JsonbAdapter<List<RymRepository>, JsonArray>
{
    @Override
    public JsonArray adaptToJson(
        List<RymRepository> repositories) throws Exception
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        repositories.forEach(r -> builder.add(r.getLocation()));
        return builder.build();
    }

    @Override
    public List<RymRepository> adaptFromJson(
        JsonArray array) throws Exception
    {
        List<RymRepository> repositories = null;

        if (array != null)
        {
            List<RymRepository> newRepositories = new ArrayList<>();
            array.forEach(v -> newRepositories.add(adaptEntryFromJson(v)));
            repositories = newRepositories;
        }

        return repositories;
    }

    private RymRepository adaptEntryFromJson(
        JsonValue value)
    {
        RymRepository repository = null;

        switch (value.getValueType())
        {
        case STRING:
            JsonString location = (JsonString) value;
            repository = new RymRepository(location.getString());
            break;
        default:
            throw new IllegalArgumentException();
        }

        return repository;
    }
}
