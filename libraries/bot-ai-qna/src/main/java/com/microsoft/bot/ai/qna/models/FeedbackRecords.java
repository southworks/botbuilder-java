// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna.models;

/**
 * Active learning feedback records.
 */
public class FeedbackRecords {
    @JsonProperty("feedbackRecords")
    private String[] records;

    /**
     * Gets the list of feedback records.
     * @return List of {@link FeedbackRecord}.
     */
    public String[] getRecords() {
        return this.records;
    }

    /**
     * Sets the list of feedback records.
     * @param records List of {@link FeedbackRecord}.
     */
    public void setRecords(String[] records) {
        this.records = records;
    }
}
