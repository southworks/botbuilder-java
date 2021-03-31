// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.nlp.with.dispatch;

import com.microsoft.bot.ai.luis.LuisApplication;
import com.microsoft.bot.ai.luis.LuisRecognizer;
import com.microsoft.bot.ai.luis.LuisRecognizerOptionsV3;
import com.microsoft.bot.ai.qna.QnAMaker;
import com.microsoft.bot.ai.qna.QnAMakerEndpoint;
import com.microsoft.bot.integration.Configuration;

/**
 * Class which implements the BotServices interface to handle
 * the services attached to the bot.
 */
public class BotServicesImpl implements BotServices {

    private LuisRecognizer dispatch;
    private QnAMaker sampleQnA;

    /**
     * Initializes a new instance of the {@link BotServicesImpl} class.
     * @param configuration A {@link Configuration} which contains the properties of the application.properties
     */
    public BotServicesImpl(Configuration configuration) {
        // Read the setting for cognitive services (LUIS, QnA) from the application.properties
        // If includeApiResults is set to true, the full response from the LUIS api (LuisResult)
        // will be made available in the properties collection of the RecognizerResult

        LuisApplication luisApplication = new LuisApplication(configuration.getProperty("LuisAppId"),
            configuration.getProperty("LuisAPIKey"),
            String.format("https://%s.api.cognitive.microsoft.com", configuration.getProperty("LuisAPIHostName")));

        // Set the recognizer options depending on which endpoint version you want to use.
        // More details can be found in https://docs.microsoft.com/en-gb/azure/cognitive-services/luis/luis-migration-api-v3
        LuisRecognizerOptionsV3 recognizerOptions = new LuisRecognizerOptionsV3(luisApplication);
        recognizerOptions.setIncludeAPIResults(true);
        recognizerOptions.setIncludeAllIntents(true);
        recognizerOptions.setIncludeInstanceData(true);

        dispatch = new LuisRecognizer(recognizerOptions);

        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint();
        qnAMakerEndpoint.setKnowledgeBaseId(configuration.getProperty("QnAKnowledgebaseId"));
        qnAMakerEndpoint.setEndpointKey(configuration.getProperty("QnAEndpointKey"));
        qnAMakerEndpoint.setHost(configuration.getProperty("QnAEndpointHostName"));

        sampleQnA = new QnAMaker(qnAMakerEndpoint, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LuisRecognizer getDispatch() {
        return dispatch;
    }

    /**
     * Sets the dispatch object.
     * @param withDispatch The new dispatch object.
     */
    private void setDispatch(LuisRecognizer withDispatch) {
        this.dispatch = withDispatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QnAMaker getSampleQnA() {
        return sampleQnA;
    }

    /**
     * Sets the sampleQnA object.
     * @param withSampleQnA The new sampleQnA object.
     */
    private void setSampleQnA(QnAMaker withSampleQnA) {
        this.sampleQnA = withSampleQnA;
    }
}
