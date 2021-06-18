// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.connector.authentication;

import com.microsoft.bot.connector.ConnectorClient;
import com.microsoft.bot.connector.rest.RestConnectorClient;
import java.util.concurrent.CompletableFuture;

public class ConnectorFactoryImpl extends ConnectorFactory {

    private String appId;
    private String toChannelFromBotOAuthScope;
    private String loginEndpoint;
    private Boolean validateAuthority;
    private ServiceClientCredentialsFactory credentialFactory;

    public ConnectorFactoryImpl(
                                String withAppId,
                                String withToChannelFromBotOAuthScope,
                                String withLoginEndpoint,
                                Boolean withValidateAuthority,
                                ServiceClientCredentialsFactory withCredentialFactory) {
        appId = withAppId;
        toChannelFromBotOAuthScope = withToChannelFromBotOAuthScope;
        loginEndpoint = withLoginEndpoint;
        validateAuthority = withValidateAuthority;
        credentialFactory = withCredentialFactory;
    }

    @Override
    public CompletableFuture<ConnectorClient> create(String serviceUrl, String audience) {
        // Use the credentials factory to create credentails specific to this particular cloud environment.
        return credentialFactory.createCredentials(appId,
                                            audience != null? audience : toChannelFromBotOAuthScope,
                                            loginEndpoint,
                                            validateAuthority).thenCompose(credentials -> {
            // A new connector client for making calls against this serviceUrl using credentials
            // derived from the current appId and the specified audience.
            return CompletableFuture.completedFuture(new RestConnectorClient(serviceUrl, credentials));
        });
    }
}
