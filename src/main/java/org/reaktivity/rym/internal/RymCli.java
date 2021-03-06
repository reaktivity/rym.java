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
package org.reaktivity.rym.internal;

import org.reaktivity.rym.internal.commands.clean.RymClean;
import org.reaktivity.rym.internal.commands.encrypt.RymEncrypt;
import org.reaktivity.rym.internal.commands.install.RymInstall;
import org.reaktivity.rym.internal.commands.wrap.RymWrap;

import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.help.Help;

@Cli(name = "rym",
    description = "Reaktivity Management Tool",
    defaultCommand = Help.class,
    commands =
    {
        Help.class,
        RymWrap.class,
        RymInstall.class,
        RymClean.class,
        RymEncrypt.class
    })
public final class RymCli
{
    private RymCli()
    {
        // utility class
    }
}
