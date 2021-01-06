// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation.model;

/**
 * Translation result from Translator API v3.
 */
public class TranslationResult {
    private String text;

    private String to;

    /**
     * Gets the translation result text.
     * @return Translation result.
     */
    public String getText() {
        return this.text;
    }

    /**
     * Sets the translation result text.
     * @param resultText Translation result.
     */
    public void setText(String resultText) {
        this.text = resultText;
    }

    /**
     * Gets the target language locale.
     * @return Locale.
     */
    public String getTo() {
        return this.to;
    }

    /**
     * Sets the target language locale.
     * @param locale Target locale.
     */
    public void setTo(String locale) {
        this.to = locale;
    }
}
