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
package org.reaktivity.rym.internal.settings;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.junit.Test;

public class RymSecurityTest
{
    @Test
    public void shouldReadEmptySecurity()
    {
        String text =
                "{" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymSecurity security = builder.fromJson(text, RymSecurity.class);

        assertThat(security, not(nullValue()));
        assertThat(security.secret, nullValue());
    }

    @Test
    public void shouldWriteEmptySecurity()
    {
        String expected =
                "{" +
                "}";

        RymSecurity security = new RymSecurity();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(security);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadSecurity()
    {
        String text =
                "{" +
                    "\"secret\":\"whisper\"" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        RymSecurity security = builder.fromJson(text, RymSecurity.class);

        assertThat(security, not(nullValue()));
        assertThat(security.secret, equalTo("whisper"));
    }

    @Test
    public void shouldWriteSecurity()
    {
        String expected =
                "{" +
                    "\"secret\":\"whisper\"" +
                "}";

        RymSecurity security = new RymSecurity();
        security.secret = "whisper";

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(security);

        assertEquals(expected, actual);
    }
}
