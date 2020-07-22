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

import java.util.Objects;

public final class RymDependency
{
    private String groupId;
    private String artifactId;
    private String version;

    public RymDependency(
        String groupId,
        String artifactId,
        String version)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public RymDependency()
    {
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof RymDependency))
        {
            return false;
        }

        RymDependency that = (RymDependency) obj;
        return Objects.equals(this.groupId, that.groupId) &&
                Objects.equals(this.artifactId, that.artifactId) &&
                Objects.equals(this.version, that.version);
    }
}
