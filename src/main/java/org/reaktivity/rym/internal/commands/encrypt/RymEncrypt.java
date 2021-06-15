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
package org.reaktivity.rym.internal.commands.encrypt;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static org.reaktivity.rym.internal.settings.RymSecrets.decryptSecret;
import static org.reaktivity.rym.internal.settings.RymSecrets.encryptSecret;
import static org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Random;
import java.util.Scanner;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import org.reaktivity.rym.internal.RymCommand;
import org.reaktivity.rym.internal.settings.RymSecurity;
import org.sonatype.plexus.components.cipher.PlexusCipherException;

import com.github.rvesse.airline.annotations.Command;

@Command(
    name = "encrypt",
    description = "Encrypt passwords")
public class RymEncrypt extends RymCommand
{
    private static final String SECRET_CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/";

    @Override
    public void invoke()
    {
        try
        {
            RymSecurity security = readSecurity(settingsDir);

            if (security.secret == null)
            {
                final String secret = generateSecret(32);
                security.secret = encryptSecret(secret, SYSTEM_PROPERTY_SEC_LOCATION);
                writeSecurity(security);
            }

            assert security.secret != null;

            final String secret = decryptSecret(security.secret, SYSTEM_PROPERTY_SEC_LOCATION);
            try (Scanner scanner = new Scanner(System.in))
            {
                String password = scanner.nextLine().trim();
                String encrypted = encryptSecret(password, secret);

                System.out.println(encrypted);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private RymSecurity readSecurity(
        Path settingsDir) throws IOException, PlexusCipherException
    {
        Path securityFile = settingsDir.resolve("security.json");

        RymSecurity security = new RymSecurity();

        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        if (Files.exists(securityFile))
        {
            try (InputStream in = newInputStream(securityFile))
            {
                security = builder.fromJson(in, RymSecurity.class);
            }
        }

        return security;
    }

    private void writeSecurity(
        RymSecurity security) throws IOException
    {
        Path securityFile = settingsDir.resolve("security.json");

        Jsonb builder = JsonbBuilder.newBuilder()
                .withConfig(new JsonbConfig().withFormatting(true))
                .build();

        createDirectories(settingsDir);
        try (OutputStream out = newOutputStream(securityFile))
        {
            builder.toJson(security, out);
        }
    }

    private String generateSecret(
        int length) throws PlexusCipherException
    {
        Random random = new SecureRandom();
        char[] secret = new char[length];

        for (int i = 0; i < secret.length; i++)
        {
            int charIndex = random.nextInt(SECRET_CHARS.length());
            secret[i] = SECRET_CHARS.charAt(charIndex);
        }

        return new String(secret);
    }
}
