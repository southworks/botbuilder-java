// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation;

import java.util.concurrent.CompletableFuture;
import com.microsoft.bot.integration.Configuration;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class MicrosoftTranslator {
    private static final String HOST = "https://api.cognitive.microsofttranslator.com";
    private static final String PATH = "/translate?api-version=3.0";
    private static final String URI_PARAMS = "&to=";

    private static OkHttpClient httpClient = new OkHttpClient();

    private static String key;

    public MicrosoftTranslator(Configuration configuration) {
        String key = configuration.getProperty("TranslatorKey");

        if (key == null) {
            throw new IllegalArgumentException("key");
        }

        MicrosoftTranslator.key = key;
    }

    public CompletableFuture<String> Translate(String text, String targetLocale) {
        // From Cognitive Services translation documentation:
        // https://docs.microsoft.com/en-us/azure/cognitive-services/Translator/quickstart-translator?tabs=java
        String requestBody = String.format("[{ \"Text\": \"%s\" }]", text);

        String uri =
            MicrosoftTranslator.HOST + MicrosoftTranslator.PATH + MicrosoftTranslator.URI_PARAMS + targetLocale;
        Request request = new Request.Builder()
            .url(uri)
            .header("Ocp-Apim-Subscription-Key", MicrosoftTranslator.key)
            .method(method, requestBody)
            .build();
        request.method();
    }
}
