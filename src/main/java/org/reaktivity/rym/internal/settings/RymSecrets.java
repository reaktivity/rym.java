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

import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;

public final class RymSecrets
{
    public static String decryptSecret(
        String secret,
        String passphrase) throws PlexusCipherException
    {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        return cipher.isEncryptedString(secret) ? cipher.decryptDecorated(secret, passphrase) : secret;
    }

    public static String encryptSecret(
        String secret,
        String passphrase) throws PlexusCipherException
    {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        return cipher.encryptAndDecorate(secret, passphrase);
    }

    private RymSecrets()
    {
    }
}
