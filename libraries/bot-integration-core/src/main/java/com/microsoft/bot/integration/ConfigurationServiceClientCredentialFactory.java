// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.integration;

import com.microsoft.bot.connector.authentication.MicrosoftAppCredentials;
import com.microsoft.bot.connector.authentication.PasswordServiceClientCredentialFactory;

/**
 * Credential provider which uses {@link Configuration} to lookup appId and password.
 * This will populate the {@PasswordServiceClientCredentialFactory.appId} from an configuration entry with the
 * key of {@MicrosoftAppCredentials.MICROSOFTAPPID} and the {@PasswordServiceClientCredentialFactory.password}
 * from a configuration entry with the key of {@MicrosoftAppCredentials.MICROSOFTAPPPASSWORD}.
 *
 * NOTE: if the keys are not present, a null value will be used.
 */
public class ConfigurationServiceClientCredentialFactory extends PasswordServiceClientCredentialFactory {
    /**
     * @param configuration An instance of {@link Configuration}.
     */
    public ConfigurationServiceClientCredentialFactory(Configuration configuration) {
        super(configuration.getProperty(MicrosoftAppCredentials.MICROSOFTAPPID),
              configuration.getProperty(MicrosoftAppCredentials.MICROSOFTAPPPASSWORD));
    }
}
