// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation;

import java.net.http.HttpClient;

import com.microsoft.bot.integration.Configuration;

import okhttp3.OkHttpClient;

public class MicrosoftTranslator {
    private static final String HOST = "https://api.cognitive.microsofttranslator.com";
    private static final String PATH = "/translate?api-version=3.0";
    private static final String URI_PARAMS = "&to=";

    private static OkHttpClient httpClient = new OkHttpClient();

    private static final String KEY;

    public MicrosoftTranslator(Configuration configuration) {
        String key = configuration.getProperty("TranslatorKey");

        if (key == null) {
            throw IllegalArgumentException();
        }

        MicrosoftTranslator.KEY = key;
    }
}
