// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna.utils;

import java.util.concurrent.CompletableFuture;

import com.microsoft.bot.ai.qna.models.FeedbackRecords;
import com.microsoft.bot.rest.serializer.JacksonAdapter;

import okhttp3.OkHttpClient;

/**
 * Helper class for train API.
 */
public class TrainUtils {
    private OkHttpClient httpClient;
    private QnAMakerEndpoint endpoint;

    /**
     * Initializes a new instance of the {@link TrainUtils} class.
     *
     * @param withEndpoint   QnA Maker endpoint details.
     * @param withHttpClient Http client.
     */
    public TrainUtils(QnAMakerEndpoint withEndpoint, OkHttpClient withHttpClient) {
        this.endpoint = withEndpoint;
        this.httpClient = withHttpClient;
    }

    /**
     * Train API to provide feedback.
     *
     * @param feedbackRecords Feedback record list.
     * @return A Task representing the asynchronous operation.
     */
    public CompletableFuture<Void> callTrain(FeedbackRecords feedbackRecords) {
        if (feedbackRecords == null) {
            throw new IllegalArgumentException("feedbackRecords: Feedback records cannot be null.");
        }

        if (feedbackRecords.getRecords() == null || feedbackRecords.getRecords().length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        // Call train
        this.queryTrain(feedbackRecords);
    }

    private CompletableFuture<Void> queryTrain(FeedbackRecords feedbackRecords) {
        String requestUrl = String.format("%1$s/knowledgebases/%2$s/train", this.endpoint.getHost(),
                this.endpoint.getKnowledgeBaseId());

        JacksonAdapter jacksonAdapter = new JacksonAdapter();
        String jsonRequest = jacksonAdapter.serialize(feedbackRecords);

        HttpRequestUtils httpRequestHelper = new HttpRequestUtils(this.httpClient);
        httpRequestHelper.executeHttpRequest(requestUrl, jsonRequest, this.endpoint).thenApply(response -> null);
    }
}
