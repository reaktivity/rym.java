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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.junit.Test;

public class RymConfigurationTest
{
    @Test
    public void shouldReadEmptyConfiguration()
    {
        String text =
                "{" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, nullValue());
    }

    @Test
    public void shouldWriteEmptyConfiguration()
    {
        String expected =
                "{" +
                "}";

        RymConfiguration config = new RymConfiguration();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadEmptyRepositories()
    {
        String text =
                "{" +
                    "\"repositories\":" +
                    "[" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.repositories, not(nullValue()));
        assertThat(config.repositories, emptyCollectionOf(RymRepository.class));
    }

    @Test
    public void shouldWriteEmptyRepositories()
    {
        String expected =
                "{" +
                    "\"repositories\":" +
                    "[" +
                    "]" +
                "}";

        RymConfiguration config = new RymConfiguration();
        config.repositories = Collections.emptyList();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadRespository()
    {
        String text =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.repositories, not(nullValue()));
        assertThat(config.repositories, equalTo(singletonList(new RymRepository("https://repo1.maven.org/maven2/"))));
    }

    @Test
    public void shouldWriteRepository()
    {
        String expected =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        RymConfiguration config = new RymConfiguration();
        config.repositories = Collections.singletonList(
                new RymRepository("https://repo1.maven.org/maven2/"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadRepositories()
    {
        String text =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://maven.example.com/maven2/\"," +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.repositories, not(nullValue()));
        assertThat(config.repositories, equalTo(asList(new RymRepository("https://maven.example.com/maven2/"),
                new RymRepository("https://repo1.maven.org/maven2/"))));
    }

    @Test
    public void shouldWriteRepositories()
    {
        String expected =
                "{" +
                    "\"repositories\":" +
                    "[" +
                        "\"https://maven.example.com/maven2/\"," +
                        "\"https://repo1.maven.org/maven2/\"" +
                    "]" +
                "}";

        RymConfiguration config = new RymConfiguration();
        config.repositories = Arrays.asList(
                new RymRepository("https://maven.example.com/maven2/"),
                new RymRepository("https://repo1.maven.org/maven2/"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadEmptyDependencies()
    {
        String text =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, not(nullValue()));
        assertThat(config.dependencies, emptyCollectionOf(RymDependency.class));
    }

    @Test
    public void shouldWriteEmptyDependencies()
    {
        String expected =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                    "]" +
                "}";

        RymConfiguration config = new RymConfiguration();
        config.dependencies = Collections.emptyList();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadDependency()
    {
        String text =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"org.reaktivity:reaktor:1.0.0\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, equalTo(singletonList(new RymDependency("org.reaktivity", "reaktor", "1.0.0"))));
    }

    @Test
    public void shouldWriteDependency()
    {
        String expected =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"org.reaktivity:reaktor:1.0.0\"" +
                    "]" +
                "}";

        RymConfiguration config = new RymConfiguration();
        config.dependencies = Collections.singletonList(
                new RymDependency("org.reaktivity", "reaktor", "1.0.0"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadDependencies()
    {
        String text =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"org.reaktivity:reaktor:1.0.0\"," +
                        "\"org.reaktivity:nukleus-tcp:1.0.0\"" +
                    "]" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymConfiguration config = builder.fromJson(text, RymConfiguration.class);

        assertThat(config, not(nullValue()));
        assertThat(config.dependencies, equalTo(asList(new RymDependency("org.reaktivity", "reaktor", "1.0.0"),
                new RymDependency("org.reaktivity", "nukleus-tcp", "1.0.0"))));
    }

    @Test
    public void shouldWriteDependencies()
    {
        String expected =
                "{" +
                    "\"dependencies\":" +
                    "[" +
                        "\"org.reaktivity:reaktor:1.0.0\"," +
                        "\"org.reaktivity:nukleus-tcp:1.0.0\"" +
                    "]" +
                "}";

        RymConfiguration config = new RymConfiguration();
        config.dependencies = Arrays.asList(
                new RymDependency("org.reaktivity", "reaktor", "1.0.0"),
                new RymDependency("org.reaktivity", "nukleus-tcp", "1.0.0"));

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(config);

        assertEquals(expected, actual);
    }
}
