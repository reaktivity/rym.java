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
package org.reaktivity.rym.internal.commands.clean;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;
import org.reaktivity.rym.internal.RymCli;

import com.github.rvesse.airline.Cli;

public class RymCleanTest
{
    @Test
    public void shouldCleanup()
    {
        String[] args = { "clean", "--output-directory", "target/rym" };

        Cli<Runnable> parser = new Cli<>(RymCli.class);
        Runnable clean = parser.parse(args);

        clean.run();

        assertThat(clean, instanceOf(RymClean.class));
    }
}