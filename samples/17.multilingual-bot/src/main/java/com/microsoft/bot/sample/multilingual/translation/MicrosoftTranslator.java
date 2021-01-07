// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.integration.Configuration;

import org.slf4j.LoggerFactory;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
        String json = String.format("[{ \"Text\": \"%s\" }]", text);

        String uri =
            MicrosoftTranslator.HOST + MicrosoftTranslator.PATH + MicrosoftTranslator.URI_PARAMS + targetLocale;

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
            .url(uri)
            .header("Ocp-Apim-Subscription-Key", MicrosoftTranslator.key)
            .post(requestBody)
            .build();

        OkHttpClient client = new OkHttpClient();

        try {
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                String message = "The call to the translation service returned HTTP status code " + response.code();
                throw new Exception(message);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            TranslatorResponse[] car = objectMapper.readValue(response.body(), TranslatorResponse.class);

        } catch (Exception e) {
            LoggerFactory.getLogger(MicrosoftTranslator.class)
                    .error("findPackages", e);
                throw new CompletionException(e);
        }
    }
}
