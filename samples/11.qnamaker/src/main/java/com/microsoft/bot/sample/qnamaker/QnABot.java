// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.qnamaker;

import com.microsoft.bot.ai.qna.QnAMaker;
import com.microsoft.bot.ai.qna.QnAMakerEndpoint;
import com.microsoft.bot.ai.qna.QnAMakerOptions;
import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.integration.Configuration;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;

public class QnABot extends ActivityHandler {
    private static Configuration configuration;

    public QnABot(Configuration withConfiguration) {
        configuration = withConfiguration;
    }

    @Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint();
        qnAMakerEndpoint.setKnowledgeBaseId(configuration.getProperty("QnAKnowledgebaseId"));
        qnAMakerEndpoint.setEndpointKey(configuration.getProperty("QnAEndpointKey"));
        qnAMakerEndpoint.setHost(configuration.getProperty("QnAEndpointHostName"));

        QnAMaker qnAMaker = new QnAMaker(qnAMakerEndpoint, null);

        (LoggerFactory.getLogger(QnABot.class)).info("Calling QnA Maker");

        QnAMakerOptions options = new QnAMakerOptions();
        options.setTop(1);

        // The actual call to the QnA Maker service.
        QueryResult[] response = qnaMaker.getAnswers(turnContext, options);
        if (response != null && response.length > 0) {
            turnContext.sendActivity(MessageFactory.text(response[0].getAnswer()));
        }
        else {
            turnContext.sendActivity(MessageFactory.text("No QnA Maker answers were found."));
        }
    }
}
