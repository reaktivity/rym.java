/**
 * Copyright 2016-2021 The Reaktivity Project
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

import java.util.Objects;

import javax.json.bind.annotation.JsonbTypeAdapter;

import org.reaktivity.rym.internal.commands.install.adapters.RymRepositoryAdapter;

@JsonbTypeAdapter(RymRepositoryAdapter.class)
public final class RymRepository
{
    public String location;

    public RymRepository()
    {
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(location);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof RymRepository))
        {
            return false;
        }

        RymRepository that = (RymRepository) obj;
        return Objects.equals(this.location, that.location);
    }

    RymRepository(
        String location)
    {
        this.location = location;
    }
}
