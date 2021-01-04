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
package org.reaktivity.rym.internal.commands.install.cache;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RymModule
{
    public final String name;
    public final Set<Path> paths;
    public final Set<RymModule> depends;
    public final boolean automatic;

    public RymModule()
    {
        this.name = "__unnamed__";
        this.paths = new LinkedHashSet<>();
        this.depends = emptySet();
        this.automatic = false;
    }

    public RymModule(
        String id,
        Path path,
        boolean automatic)
    {
        this.name = id;
        this.paths = singleton(path);
        this.depends = new LinkedHashSet<>();
        this.automatic = automatic;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, paths, depends, automatic);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof RymModule))
        {
            return false;
        }

        RymModule that = (RymModule) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.paths, that.paths) &&
                Objects.equals(this.depends, that.depends) &&
                this.automatic == that.automatic;
    }

    @Override
    public String toString()
    {
        return String.format("%s%s -> %s %s",
                name, automatic ? "+" : "", depends.stream().map(m ->  m.name).collect(toSet()), paths);
    }
}
