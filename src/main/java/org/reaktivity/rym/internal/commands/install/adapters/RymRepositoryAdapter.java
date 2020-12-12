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

import javax.json.Json;
import javax.json.JsonString;
import javax.json.bind.adapter.JsonbAdapter;

import org.reaktivity.rym.internal.commands.install.RymRepository;

public final class RymRepositoryAdapter implements JsonbAdapter<RymRepository, JsonString>
{
    @Override
    public JsonString adaptToJson(
        RymRepository repository)
    {
        return Json.createValue(repository.location);
    }

    @Override
    public RymRepository adaptFromJson(
        JsonString location)
    {
        RymRepository repository = new RymRepository();
        repository.location = location.getString();
        return repository;
    }
}
