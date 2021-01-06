// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Array of translated results from Translator API v3.
 */
public class TranslationResponse {
    @JsonProperty("translations")
    private List<TranslationResult> translations;

    /**
     * Gets the translation results.
     * @return A list of {@link TranslationResult}
     */
    public List<TranslationResult> getTranslations() {
        return this.translations;
    }

    /**
     * Sets the translation results.
     * @param translationResults A list of {@link TranslationResult}
     */
    public void setTranslations(List<TranslationResult> withTranslation) {
        this.translations = translationResults;
    }
}
