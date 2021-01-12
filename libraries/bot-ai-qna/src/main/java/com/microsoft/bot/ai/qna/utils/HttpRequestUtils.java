// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna.utils;

import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Request.Builder;

/**
 * Helper for HTTP requests.
 */
public class HttpRequestUtils {
    private static ProductInfoHeaderValue BOT_BUILDER_INFO;
    private static ProductInfoHeaderValue PLATFORM_INFO;

    private OkHttpClient httpClient;

    /**
     * Initializes a new instance of the {@link HttpRequestUtils} class.
     *
     * @param withHttpClient Http client.
     */
    public HttpRequestUtils(OkHttpClient withHttpClient) {
        this.httpClient = withHttpClient;
        this.updateBotBuilderAndPlatformInfo();
    }

    /**
     * Execute Http request.
     *
     * @param requestUrl  Http request url.
     * @param payloadBody Http request body.
     * @param endpoint    QnA Maker endpoint details.
     * @return Returns http response object.
     */
    public CompletableFuture<Response> executeHttpRequest(String requestUrl, String payloadBody,
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

        try (String string = new String()) {

        }
    }

    private static void setHeaders(Request request, QnAMakerEndpoint endpoint) {
    }

    private void updateBotBuilderAndPlatformInfo() {

    }
}
