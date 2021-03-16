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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.reaktivity.rym.internal.RymCommand;
import org.reaktivity.rym.internal.types.RymPathConverterProvider;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

@Command(
    name = "wrap",
    description = "Generate wrapper")
public class RymWrap extends RymCommand
{
    @Option(name = { "--repository" })
    public String repoURL = "https://repo.maven.apache.org/maven2";

    @Option(name = { "--version" })
    public String version = "0.1";

    @Option(name = { "--rymw-directory" },
            description = "rymw directory",
            typeConverterProvider = RymPathConverterProvider.class)
    public Path rymwDir = Paths.get(".rymw");

    private Path wrappedPath;
    private String wrappedURL;

    @Override
    public void invoke()
    {
        wrappedPath = outputDir.resolve("wrapper").resolve("rym.jar");
        wrappedURL = String.format("%s/org/reaktivity/rym/%s/rym-%s.jar", repoURL, version, version);

        try
        {
            generateWrapper();
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private void generateWrapper() throws IOException
    {
        Path rymwPath = launcherDir.resolve("rymw");
        Files.write(rymwPath, Arrays.asList(
                "#!/bin/sh",
                String.format("wrappedPath=\"%s\"", wrappedPath),
                String.format("wrappedURL=\"%s\"", wrappedURL),
                "if [ ! -r \"$wrappedPath\" ]; then",
                  "echo $wrappedPath not found, downloading from $wrappedURL",
                  "if command -v curl > /dev/null; then",
                    "curl -o \"$wrappedPath\" \"$wrappedURL\" -f",
                  "fi",
                "fi",
                "java -jar \"$wrappedPath\" \"$@\""));
        rymwPath.toFile().setExecutable(true);
    }
}
