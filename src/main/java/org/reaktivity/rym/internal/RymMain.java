/**
 * Copyright 2016-2019 The Reaktivity Project
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

import java.io.IOException;
import java.util.Arrays;

import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.help.Help;
import com.github.rvesse.airline.parser.errors.ParseCommandMissingException;
import com.github.rvesse.airline.parser.errors.ParseCommandUnrecognizedException;

@Cli(
    name = "rym",
    description = "Reaktivity Management Tool",
    commands = { Help.class })
public final class RymMain
{

    public static void main(String[] args)
    {
        com.github.rvesse.airline.Cli<Runnable> parser = new com.github.rvesse.airline.Cli<>(RymMain.class);

        try
        {
            parser.parse(args).run();
        }
        catch (ParseCommandMissingException e)
        {
            showUsage(parser);
        }
        catch (ParseCommandUnrecognizedException e)
        {
            if (!args[0].equalsIgnoreCase("--help"))
            {
                System.err.format("%s\n\n", e.getMessage());
            }
            showUsage(parser);
        }
    }

    private RymMain()
    {
    }

    private static void showUsage(com.github.rvesse.airline.Cli<Runnable> parser)
    {
        try
        {
            Help.help(parser.getMetadata(), Arrays.asList());
        }
        catch (IOException e)
        {
        }
    }
}
