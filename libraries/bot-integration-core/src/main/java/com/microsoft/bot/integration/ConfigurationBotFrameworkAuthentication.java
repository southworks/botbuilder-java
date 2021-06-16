// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.integration;

import com.microsoft.bot.builder.skills.BotFrameworkClient;
import com.microsoft.bot.connector.authentication.AuthenticateRequestResult;
import com.microsoft.bot.connector.authentication.AuthenticationConfiguration;
import com.microsoft.bot.connector.authentication.AuthenticationConstants;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthenticationFactory;
import com.microsoft.bot.connector.authentication.ClaimsIdentity;
import com.microsoft.bot.schema.Activity;

import java.util.concurrent.CompletableFuture;

/**
 * Creates a BotFrameworkAuthentication instance from configuration.
 */
public class ConfigurationBotFrameworkAuthentication extends BotFrameworkAuthentication {
    private final BotFrameworkAuthentication inner;

    /**
     * Initializes a new instance of the ConfigurationBotFrameworkAuthentication class.
     * @param configuration A Configuration instance.
     * @param credentialsFactory An ServiceClientCredentialsFactory instance.
     * @param authConfiguration An AuthenticationConfiguration instance.
     */
    public ConfigurationBotFrameworkAuthentication(Configuration configuration,
                                                   ServiceClientCredentialsFactory credentialsFactory,
                                                   AuthenticationConfiguration authConfiguration) {
        String channelService = configuration.getProperty("ChannelService");
        String validateAuthority = configuration.getProperty("ValidateAuthority");
        String toChannelFromBotLoginUrl = configuration.getProperty("ToChannelFromBotLoginUrl");
        String toChannelFromBotOAuthScope = configuration.getProperty("ToChannelFromBotOAuthScope");

        String toBotFromChannelTokenIssuer = configuration.getProperty("ToBotFromChannelTokenIssuer") != null ?
                                             configuration.getProperty("ToBotFromChannelTokenIssuer") :
                                             configuration.getProperty(AuthenticationConstants.BOT_OPENID_METADATA_KEY);
        String oAuthUrl = configuration.getProperty("OAuthUrl") != null ?
                          configuration.getProperty("OAuthUrl") :
                          configuration.getProperty(AuthenticationConstants.OAUTH_URL_KEY);
        String toBotFromChannelOpenIdMetadataUrl = configuration.getProperty("ToBotFromChannelOpenIdMetadataUrl");
        String toBotFromEmulatorOpenIdMetadataUrl = configuration.getProperty("ToBotFromEmulatorOpenIdMetadataUrl");
        String callerId = configuration.getProperty("CallerId");

        inner = BotFrameworkAuthenticationFactory.create(
            channelService,
            validateAuthority == null || Boolean.parseBoolean(validateAuthority),
            toChannelFromBotLoginUrl,
            toChannelFromBotOAuthScope,
            toBotFromChannelTokenIssuer,
            oAuthUrl,
            toBotFromChannelOpenIdMetadataUrl,
            toBotFromEmulatorOpenIdMetadataUrl,
            callerId,
            credentialsFactory != null ? credentialsFactory : new ConfigurationServiceClientCredentialFactory(configuration),
            authConfiguration != null ? authConfiguration : new AuthenticationConfiguration());
    }

    @Override
    public String getOriginatingAudience() {
        return inner.getOriginatingAudience();
    }

    @Override
    public CompletableFuture<ClaimsIdentity> authenticateChannelRequest(String authHeader) {
        return inner.authenticateChannelRequest(authHeader);
    }

    @Override
    public CompletableFuture<AuthenticateRequestResult> authenticateRequest(Activity activity, String authHeader) {
        return authenticateRequest(activity, authHeader);
    }

    @Override
    public CompletableFuture<AuthenticateRequestResult> authenticateStreamingRequest(String authHeader, String channelIdHeader) {
        return inner.authenticateStreamingRequest(authHeader, channelIdHeader);
    }

    @Override
    public ConnectorFactory createConnectorFactory(ClaimsIdentity claimsIdentity) {
        return inner.createConnectorFactory(claimsIdentity);
    }

    @Override
    public CompletableFuture<UserTokenClient> createUserTokenClient(ClaimsIdentity claimsIdentity) {
        return inner createUserTokenClient(claimsIdentity);
    }

    @Override
    public BotFrameworkClient createBotFrameworkClient() {
        return inner.createBotFrameworkClient();
    }
}
