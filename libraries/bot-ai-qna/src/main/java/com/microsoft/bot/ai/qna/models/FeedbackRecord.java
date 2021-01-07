// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna.models;

public class FeedbackRecod {
    @JsonProperty("userId")
    private String userId;

    @JsonProperty("userQuestion")
    private String userQuestion;

    @JsonProperty("qnaId")
    private String qnaId;

    /**
     * Gets the user ID.
     * @return The user ID.
     */
    public String getUserId() {
        return this.userId;
    }

    /**
     * Sets the user ID.
     * @param userId The user ID.
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the user question.
     * @return The user question.
     */
    public String getUserQuestion() {
        return this.userQuestion;
    }

    /**
     * Sets the user question.
     * @param userQuestion The user question.
     */
    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    /**
     * Gets the QnA ID.
     * @return The QnA ID.
     */
    public String getQnaId() {
        return this.qnaId;
    }

    /**
     * Sets the QnA ID.
     * @param qnaId The QnA ID.
     */
    public void setQnaId(String qnaId) {
        this.qnaId = qnaId;
    }
}
