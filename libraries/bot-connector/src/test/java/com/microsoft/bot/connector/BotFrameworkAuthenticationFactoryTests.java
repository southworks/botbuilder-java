// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.connector;

import com.microsoft.bot.connector.authentication.AuthenticationConstants;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthentication;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthenticationFactory;
import com.microsoft.bot.connector.authentication.GovernmentAuthenticationConstants;
import com.microsoft.bot.connector.authentication.PasswordServiceClientCredentialFactory;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;

public class BotFrameworkAuthenticationFactoryTests {
    @Test
    public void shouldCreateAnonymousBotFrameworkAuthentication() {
        BotFrameworkAuthentication botFrameworkAuthentication = BotFrameworkAuthenticationFactory.create();
        Assert.assertThat(botFrameworkAuthentication, instanceOf(BotFrameworkAuthentication.class));
    }

    @Test
    public void shouldCreateBotFrameworkAuthenticationConfiguredForValidChannelServices() {
        BotFrameworkAuthentication botFrameworkAuthentication = BotFrameworkAuthenticationFactory.create(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        Assert.assertEquals(
            botFrameworkAuthentication.getOriginatingAudience(),
            AuthenticationConstants.TO_CHANNEL_FROM_BOT_OAUTH_SCOPE);

        BotFrameworkAuthentication governmentBotFrameworkAuthentication = BotFrameworkAuthenticationFactory.create(
            GovernmentAuthenticationConstants.CHANNELSERVICE,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        Assert.assertEquals(
            governmentBotFrameworkAuthentication.getOriginatingAudience(),
            GovernmentAuthenticationConstants.TO_CHANNEL_FROM_BOT_OAUTH_SCOPE);
    }

    public void shouldThrowWithAnUnknownChannelService() {
        Assert.assertThrows(IllegalArgumentException.class, ()-> { BotFrameworkAuthenticationFactory.create(
            "Unknown",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);});
    }

    public void shouldNotThrowErrorsWhenAuthIsDisabledAndAnonymousSkillClaimsAreUsed() {
        PasswordServiceClientCredentialFactory credentialFactory = new PasswordServiceClientCredentialFactory("", "");
        BotFrameworkAuthentication botFrameworkAuthentication = BotFrameworkAuthenticationFactory.create(
            "",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            credentialFactory,
            null,
            null);

        Assert.assertEquals(botFrameworkAuthentication.getOriginatingAudience(), AuthenticationConstants.TO_CHANNEL_FROM_BOT_OAUTH_SCOPE);
    }

    public void shouldNotThrowErrorsWhenAuthIsDisabledAndAuthenticatedSkillClaimsAreUsed() {
        String APP_ID = "app-id";
        String APP_PASSWORD = "app-password";
        PasswordServiceClientCredentialFactory credentialFactory = new PasswordServiceClientCredentialFactory(APP_ID, APP_PASSWORD);
        BotFrameworkAuthentication botFrameworkAuthentication = BotFrameworkAuthenticationFactory.create(
            "",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            credentialFactory,
            null,
            null);

        Assert.assertEquals(botFrameworkAuthentication.getOriginatingAudience(), AuthenticationConstants.TO_CHANNEL_FROM_BOT_OAUTH_SCOPE);
    }
}
