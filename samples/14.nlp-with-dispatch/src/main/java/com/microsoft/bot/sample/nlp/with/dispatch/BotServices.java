// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.nlp.with.dispatch;

import com.microsoft.bot.ai.luis.LuisRecognizer;
import com.microsoft.bot.ai.qna.QnAMaker;

/**
 * Interface which contains the LuisRecognizer dispatch and the QnAMaker application.
 */
public interface BotServices {

    /**
     * Gets the {@link LuisRecognizer} dispatch
     * @return The LuisRecognizer dispatch
     */
    LuisRecognizer getDispatch();


    /**
     * Gets the {@link QnAMaker} application
     * @return The QnAMaker application
     */
    QnAMaker getSampleQnA();
}
