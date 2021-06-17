package com.microsoft.bot.connector.authentication;

import org.apache.commons.lang3.NotImplementedException;
import java.util.concurrent.CompletableFuture;

/**
 * This is just an internal class to allow pre-existing implementation of the request validation to be used with
 * a IServiceClientCredentialFactory.
 */
public class DelegatingCredentialProvider implements ICredentialProvider {
    private ServiceClientCredentialsFactory credentialsFactory;

    public DelegatingCredentialProvider(ServiceClientCredentialsFactory withCredentialsFactory) {
        if (withCredentialsFactory == null) {
            throw new IllegalArgumentException();
        }

        credentialsFactory = withCredentialsFactory;
    }

    public CompletableFuture<String> getAppPassword(String appId) {
        throw new NotImplementedException("");
    }

    public CompletableFuture<Boolean> isAuthenticationDisabled() {
        return credentialsFactory.isAuthenticationDisabled();
    }

    public CompletableFuture<Boolean> isValidAppId(String appId) {
        return credentialsFactory.isValidAppId(appId);
    }
}

