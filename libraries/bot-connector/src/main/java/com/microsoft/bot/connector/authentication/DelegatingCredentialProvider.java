// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.connector.authentication;

import org.apache.commons.lang3.NotImplementedException;
import java.util.concurrent.CompletableFuture;

/**
 * This is just an internal class to allow pre-existing implementation of the request validation to be used with
 * a IServiceClientCredentialFactory.
 */
public class DelegatingCredentialProvider implements CredentialProvider {
    private ServiceClientCredentialsFactory credentialsFactory;

    public DelegatingCredentialProvider(ServiceClientCredentialsFactory withCredentialsFactory) {
        if (withCredentialsFactory == null) {
            throw new IllegalArgumentException("withCredentialsFactory cannot be null");
        }

        credentialsFactory = withCredentialsFactory;
    }

    public CompletableFuture<String> getAppPassword(String appId) {
        throw new NotImplementedException("getAppPassword is not implemented");
    }

    public CompletableFuture<Boolean> isAuthenticationDisabled() {
        return credentialsFactory.isAuthenticationDisabled();
    }

    public CompletableFuture<Boolean> isValidAppId(String appId) {
        return credentialsFactory.isValidAppId(appId);
    }
}

