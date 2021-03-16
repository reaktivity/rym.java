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
package org.reaktivity.rym.internal.commands.wrap;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;
import org.reaktivity.rym.internal.RymCli;

import com.github.rvesse.airline.Cli;

public class RymWrapTest
{
    @Test
    public void shouldWrap()
    {
        String[] args =
        {
            "wrap",
            "--version", "1.0",
            "--output-directory", "target/rym",
            "--launcher-directory", "target"
        };

        Cli<Runnable> parser = new Cli<>(RymCli.class);
        Runnable wrap = parser.parse(args);

        wrap.run();

        assertThat(wrap, instanceOf(RymWrap.class));
    }
}
