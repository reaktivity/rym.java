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
package org.reaktivity.rym.internal.commands.clean;

import static java.util.Comparator.reverseOrder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.reaktivity.rym.internal.RymCommand;

import com.github.rvesse.airline.annotations.Command;

@Command(
    name = "clean",
    description = "Clean up")
public final class RymClean extends RymCommand
{
    @Override
    public void invoke()
    {
        try
        {
            deleteDirectories(outputDir);
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private void deleteDirectories(
        Path dir) throws IOException
    {
        if (Files.exists(dir))
        {
            Files.walk(dir)
                 .sorted(reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
}
