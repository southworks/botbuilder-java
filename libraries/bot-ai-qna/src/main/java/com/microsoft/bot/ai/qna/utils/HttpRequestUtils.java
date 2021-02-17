// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.ai.qna.QnAMakerEndpoint;
import com.microsoft.bot.connector.UserAgent;

import okhttp3.*;
import org.slf4j.LoggerFactory;

/**
 * Helper for HTTP requests.
 */
public class HttpRequestUtils {
    private OkHttpClient httpClient = new OkHttpClient();
    /**
     * Execute Http request.
     *
     * @param requestUrl  Http request url.
     * @param payloadBody Http request body.
     * @param endpoint    QnA Maker endpoint details.
     * @return Returns http response object.
     */
    public CompletableFuture<JsonNode> executeHttpRequest(String requestUrl, String payloadBody,
            QnAMakerEndpoint endpoint) {
        if (requestUrl == null) {
            throw new IllegalArgumentException("requestUrl: Request url can not be null.");
        }

        if (payloadBody == null) {
            throw new IllegalArgumentException("payloadBody: Payload body can not be null.");
        }

        if (endpoint == null) {
            throw new IllegalArgumentException("endpoint");
        }

        ObjectMapper mapper = new ObjectMapper();
        String endpointKey = String.format("%s", endpoint.getEndpointKey());
        Response response;
        JsonNode qnaResponse = null;
        try {
            Request request = buildRequest(requestUrl, endpointKey, buildRequestBody(payloadBody));
            response = this.httpClient.newCall(request).execute();
            qnaResponse = mapper.readTree(response.body().string());
            if (!response.isSuccessful()) {
                String message = new StringBuilder("The call to the translation service returned HTTP status code ")
                    .append(response.code()).append(".").toString();
                throw new Exception(message);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(HttpRequestUtils.class).error("findPackages", e);
            throw new CompletionException(e);
        }

        return CompletableFuture.completedFuture(qnaResponse);
    }

    private Request buildRequest(String requestUrl, String endpointKey, RequestBody body) {
        HttpUrl.Builder httpBuilder = HttpUrl.parse(requestUrl).newBuilder();
        Request.Builder requestBuilder = new Request.Builder()
            .url(httpBuilder.build())
            .addHeader("Authorization", String.format("EndpointKey %s", endpointKey))
            .addHeader("Ocp-Apim-Subscription-Key", endpointKey).addHeader("User-Agent", UserAgent.value());
        return requestBuilder.build();
    }

    private RequestBody buildRequestBody(String payloadBody) throws JsonProcessingException {
        return RequestBody.create(payloadBody,  MediaType.parse("application/json; charset=utf-8"));
    }
}
