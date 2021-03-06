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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.reaktivity.rym.internal.RymCli;

import com.github.rvesse.airline.Cli;

public class RymInstallTest
{
    @Test
    public void shouldInstallEcho() throws IOException
    {
        String[] args =
        {
            "install",
            "--config-directory", "src/test/conf/install",
            "--lock-directory", "target/test-locks/install",
            "--output-directory", "target/rym",
            "--launcher-directory", "target",
            "--silent"
        };

        Cli<Runnable> parser = new Cli<>(RymCli.class);
        Runnable install = parser.parse(args);

        install.run();

        assertThat(install, instanceOf(RymInstall.class));
        assertThat(new File("src/test/conf/install/rym.json"), anExistingFile());
        assertThat(new File("target/test-locks/install/rym-lock.json"), anExistingFile());
        assertThat(new File("target/rym/cache/org.reaktivity/ry/jars/ry-0.57.jar"), anExistingFile());
        assertThat(new File("target/rym/cache/org.reaktivity/reaktor/jars/reaktor-0.166.jar"), anExistingFile());
        assertThat(new File("target/rym/cache/org.reaktivity/nukleus-echo/jars/nukleus-echo-0.25.jar"), anExistingFile());
        assertThat(new File("target/rym/cache/org.agrona/agrona/jars/agrona-1.6.0.jar"), anExistingFile());
    }
}
