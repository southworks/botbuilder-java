// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna.models;

/**
 * Enumeration of types of ranking.
 */
public class RankerTypes {

    /**
     * Default Ranker Behaviour. i.e. Ranking based on Questions and Answer.
     */
    public static final String DEFAULTRANKERTYPE = "Default";

    /**
     * Ranker based on question Only.
     */
    public static final String QUESTIONONLY = "QuestionOnly";

    /**
     * Ranker based on Autosuggest for question field Only.
     */
    public static final String AUTOSUGGESTQUESTION = "AutoSuggestQuestion";
}
