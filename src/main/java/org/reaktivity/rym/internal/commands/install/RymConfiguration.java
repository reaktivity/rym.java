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

import java.util.List;
import java.util.Objects;

public final class RymConfiguration
{
    public List<RymDependency> dependencies;
    public List<RymDependency> imports;
    public List<RymRepository> repositories;

    @Override
    public int hashCode()
    {
        return Objects.hash(dependencies, imports, repositories);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof RymConfiguration))
        {
            return false;
        }

        RymConfiguration that = (RymConfiguration) obj;
        return Objects.deepEquals(this.dependencies, that.dependencies) &&
                Objects.deepEquals(this.imports, that.imports) &&
                Objects.deepEquals(this.repositories, that.repositories);
    }
}
