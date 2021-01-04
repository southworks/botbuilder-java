// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.dialogs.prompts;

import com.microsoft.bot.connector.authentication.AppCredentials;

/**
 * Contains settings for an {@link OAuthPrompt}.
 */
public class OAuthPromptSettings {
    private AppCredentials oAuthAppCredentials;
    private String connectionName;
    private String title;
    private String text;
    private Integer timeout;
    private Boolean endOnInvalidMessage;

    /**
     * Gets the OAuthAppCredentials for OAuthPrompt.
     * @return The AppCredentials for OAuthPrompt.
     */
    public AppCredentials getOAuthAppCredentials() {
        return this.oAuthAppCredentials;
    }

    /**
     * Sets the OAuthAppCredentials for OAuthPrompt.
     * @param oAuthAppCredentials The AppCredentials for OAuthPrompt.
     */
    public void setOAuthAppCredentials(AppCredentials oAuthAppCredentials) {
        this.oAuthAppCredentials = oAuthAppCredentials;
    }

    /**
     * Gets the name of the OAuth connection.
     * @return The name of the OAuth connection.
     */
    public String getConnectionName() {
        return this.connectionName;
    }

    /**
     * Sets the name of the OAuth connection.
     * @param connectionName The name of the OAuth connection.
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * Gets the title of the sign-in card.
     * @return The title of the sign-in card.
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the title of the sign-in card.
     * @param title The title of the sign-in card.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets any additional text to include in the sign-in card.
     * @return Any additional text to include in the sign-in card.
     */
    public String getText() {
        return this.text;
    }

    /**
     * Sets any additional text to include in the sign-in card.
     * @param text Any additional text to include in the sign-in card.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets the number of milliseconds the prompt waits for the user to authenticate.
     * Default is 900,000 (15 minutes).
     * @return The number of milliseconds the prompt waits for the user to authenticate.
     */
    public Integer getTimeout() {
        return this.timeout;
    }

    /**
     * Sets the number of milliseconds the prompt waits for the user to authenticate.
     * Default is 900,000 (15 minutes).
     * @param timeout The number of milliseconds the prompt waits for the user to authenticate.
     */
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets a value indicating whether the {@link OAuthPrompt} should end upon
     * receiving an invalid message.  Generally the {@link OAuthPrompt} will ignore
     * incoming messages from the user during the auth flow, if they are not related to the
     * auth flow.  This flag enables ending the {@link OAuthPrompt} rather than
     * ignoring the user's message.  Typically, this flag will be set to 'true', but is 'false'
     * by default for backwards compatibility.
     * True if the {@link OAuthPrompt} should automatically end upon receiving
     * an invalid message.
     * @return The number of milliseconds the prompt waits for the user to authenticate.
     */
    public Boolean getEndOnInvalidMessage() {
        return this.endOnInvalidMessage;
    }

    /**
     * Sets a value indicating whether the {@link OAuthPrompt} should end upon
     * receiving an invalid message.  Generally the {@link OAuthPrompt} will ignore
     * incoming messages from the user during the auth flow, if they are not related to the
     * auth flow.  This flag enables ending the {@link OAuthPrompt} rather than
     * ignoring the user's message.  Typically, this flag will be set to 'true', but is 'false'
     * by default for backwards compatibility.
     * True if the {@link OAuthPrompt} should automatically end upon receiving
     * an invalid message.
     * @param endOnInvalidMessage The number of milliseconds the prompt waits for the user to authenticate.
     */
    public void setEndOnInvalidMessage(Boolean endOnInvalidMessage) {
        this.endOnInvalidMessage = endOnInvalidMessage;
    }
}
