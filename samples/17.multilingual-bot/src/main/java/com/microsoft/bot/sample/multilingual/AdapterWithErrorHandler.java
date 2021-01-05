package com.microsoft.bot.sample.multilingual;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import com.microsoft.bot.builder.ConversationState;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.integration.Configuration;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

public class AdapterWithErrorHandler extends BotFrameworkHttpAdapter {
    public AdapterWithErrorHandler(Configuration configuration, TranslationMiddleware translationMiddleware, @Nullable ConversationState conversationState) {
        super(configuration);
        if (translationMiddleware == null) {
            throw new IllegalArgumentException("translationMiddleware");
        }

        // Add translation middleware to the adapter's middleware pipeline
        this.use(translationMiddleware);

        this.setOnTurnError((turnContext, exception) -> {
            Logger logger = LoggerFactory.getLogger(AdapterWithErrorHandler.class);
            // Log any leaked exception from the application.
            // NOTE: In production environment, you should consider logging this to
            // Azure Application Insights. Visit https://aka.ms/bottelemetry to see how
            // to add telemetry capture to your bot.
            logger.error(String.format("[OnTurnError] unhandled error : %s", exception.getMessage()), exception);

            // Send a message to the user
            return sendWithoutMiddleware(turnContext, "The bot encountered an error or bug.").thenCompose(task -> {
                sendWithoutMiddleware(turnContext, "To continue to run this bot, please fix the bot source code.");
                return CompletableFuture.completedFuture(null);
            }).thenCompose(task -> {
                if (conversationState != null) {
                    try {
                        // Delete the conversationState for the current conversation to prevent the
                        // bot from getting stuck in a error-loop caused by being in a bad state.
                        // ConversationState should be thought of as similar to "cookie-state" in a Web pages.
                        conversationState.delete(turnContext);
                        return CompletableFuture.completedFuture(null);
                    } catch (Exception e) {
                        logger.error(String.format("Exception caught on attempting to Delete ConversationState : %s", e.getMessage()), e);
                    }
                }

                // Send a trace activity, which will be displayed in the Bot Framework Emulator
                Activity traceActivity = new Activity(ActivityTypes.TRACE) {
                    {
                        setName("OnTurnError Trace");
                        setValue(exception.getMessage());
                        setValueType("https://www.botframework.com/schemas/error");
                        setLabel("TurnError");
                    }
                };
                return turnContext.sendActivity(traceActivity).thenApply(resourceResponse -> null);
            });
        });
    }

    private static CompletableFuture<Void> sendWithoutMiddleware(TurnContext turnContext, String message) {
        // Sending the Activity directly through the Adapter rather than through the TurnContext skips the middleware processing
        // this might be important in this particular case because it might have been the TranslationMiddleware that is actually failing!
        Activity activity = MessageFactory.text(message);

        // If we are skipping the TurnContext we must address the Activity manually here before sending it.
        activity.applyConversationReference(turnContext.getActivity().getConversationReference());

        // Send the actual Activity through the Adapter.
        return turnContext.getAdapter().sendActivities(turnContext, Arrays.asList(activity)).thenApply(resourceResponses -> null);
    }
}
