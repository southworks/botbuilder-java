// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual.translation;

import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.integration.Configuration;
import com.microsoft.bot.sample.multilingual.translation.model.TranslatorResponse;

import org.slf4j.LoggerFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A helper class wrapper for the Microsoft Translator API.
 */
public class MicrosoftTranslator {
    private static final String HOST = "https://api.cognitive.microsofttranslator.com";
    private static final String PATH = "/translate?api-version=3.0";
    private static final String URI_PARAMS = "&to=";

    private static OkHttpClient httpClient = new OkHttpClient();
    private static String key;

    /**
     * @param configuration The configuration class with the translator key stored.
     */
    public MicrosoftTranslator(Configuration configuration) {
        String translatorKey = configuration.getProperty("TranslatorKey");

        if (translatorKey == null) {
            throw new IllegalArgumentException("key");
        }

        MicrosoftTranslator.key = translatorKey;
    }

    /**
     * Helper method to translate text to a specified language.
     * @param text Text Text that will be translated.
     * @param targetLocale targetLocale Two character language code, e.g. "en", "es".
     * @return The first translation result
     */
    public CompletableFuture<String> translate(String text, String targetLocale) {
        return CompletableFuture.supplyAsync(() -> {
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

            try {
                Response response = MicrosoftTranslator.httpClient.newCall(request).execute();

                if (!response.isSuccessful()) {
                    String message =  String.format("The call to the translation service returned HTTP status code %s",
                                        response.code() + ".");
                    throw new Exception(message);
                }

                ObjectMapper objectMapper = new ObjectMapper();
                Reader reader = new StringReader(response.body().string());
                TranslatorResponse[] result = objectMapper.readValue(reader, TranslatorResponse[].class);

                return result[0].getTranslations().get(0).getText();

            } catch (Exception e) {
                LoggerFactory.getLogger(MicrosoftTranslator.class).error("findPackages", e);
                throw new CompletionException(e);
            }
        });
    }
}
