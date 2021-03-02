// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.core;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.dialogs.ComponentDialog;
import com.microsoft.bot.dialogs.DialogTurnResult;
import com.microsoft.bot.dialogs.WaterfallDialog;
import com.microsoft.bot.dialogs.WaterfallStep;
import com.microsoft.bot.dialogs.WaterfallStepContext;
import com.microsoft.bot.dialogs.prompts.PromptOptions;
import com.microsoft.bot.dialogs.prompts.TextPrompt;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.InputHints;
import com.microsoft.recognizers.datatypes.timex.expression.TimexProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

public class MainDialog extends ComponentDialog {
    private FlightBookingRecognizer luisRecognizer;
    private String MAIN_WATERFALL_DIALOG = "mainWaterfallDialog";

    public MainDialog(FlightBookingRecognizer withLuisRecognizer, BookingDialog bookingDialog) {
        super("MainDialog");

        luisRecognizer = withLuisRecognizer;

        addDialog(new TextPrompt("TextPrompt"));
        addDialog(bookingDialog);
        addDialog(new WaterfallDialog(MAIN_WATERFALL_DIALOG, new ArrayList<WaterfallStep>() {
            {
                add(introStep);
                add(actStep);
                add(finalStep);
            }
        }));

        setInitialDialogId(MAIN_WATERFALL_DIALOG);
    }

    private CompletableFuture<DialogTurnResult> introStep(WaterfallStepContext stepContext) {
        if (!luisRecognizer.isConfigured) {
            stepContext.getContext().sendActivity
                (MessageFactory.text
                    ("NOTE: LUIS is not configured. To enable all capabilities, add 'LuisAppId', 'LuisAPIKey' and 'LuisAPIHostName' to the appsettings.json file.", InputHints.IGNORING_INPUT))
                .thenCompose(sendResult -> stepContext.next(null).thenApply(nextResult -> null));
        }

        // Use the text provided in FinalStepAsync or the default if it is the first time.
        String weekLaterDate = LocalDateTime.now().plusDays(7).toString();
        String messageText = (stepContext.getOptions() != null)? stepContext.getOptions().toString() : String.format("What can I help you with today?\nSay something like \"Book a flight from Paris to Berlin on %s\"", weekLaterDate);
        Activity promptMessage = MessageFactory.text(messageText, messageText, InputHints.EXPECTING_INPUT);
        return stepContext.prompt("TextPrompt", new PromptOptions() {{ setPrompt(promptMessage); }});
    }

    private CompletableFuture<DialogTurnResult> actStep(WaterfallStepContext stepContext) {
        if (!luisRecognizer.isConfigured) {
            // LUIS is not configured, we just run the BookingDialog path with an empty BookingDetailsInstance.
            return stepContext.beginDialog("BookingDialog", new BookingDetails());
        }

        // Call LUIS and gather any potential booking details. (Note the TurnContext has the response to the prompt.)
        luisRecognizer.recognize<FlightBooking>(stepContext.getContext()).thenCompose(luisResult -> {
                switch (luisResult.topIntent().intent) {
                    case "BookFlight":
                        ObjectNode fromEntities = this.luisRecognizer.getFromEntities(luisResult);
                        ObjectNode toEntities = this.luisRecognizer.getToEntities(luisResult);

                        showWarningForUnsupportedCities(stepContext.getContext(), fromEntities, toEntities).thenApply( showResult -> {
                                // Initialize BookingDetails with any entities we may have found in the response.
                                BookingDetails bookingDetails = new BookingDetails() {
                                    {
                                        // Get destination and origin from the composite entities arrays.
                                        setDestination(luisResult.getToEntities().getAirport());
                                        setOrigin(luisResult.getFromEntities().getAirport());
                                        setTravelDate(luisResult.getTravelDate());
                                    }
                                };
                                // Run the BookingDialog giving it whatever details we have from the LUIS call, it will fill out the remainder.
                                return stepContext.beginDialog("BookingDialog", bookingDetails);
                            }
                        );
                        break;
                    case "GetWeather":
                        // We haven't implemented the GetWeatherDialog so we just display a TODO message.
                        String getWeatherMessageText = "TODO: get weather flow here";
                        Activity getWeatherMessage = MessageFactory.text(getWeatherMessageText, getWeatherMessageText, InputHints.IGNORING_INPUT);
                        return stepContext.getContext().sendActivity(getWeatherMessage);
                        break;
                    default:
                        // Catch all for unhandled intents
                        String didntUnderstandMessageText = String.format("Sorry, I didn't get that. Please try asking in a different way (intent was %s)", luisResult.topIntent()intent);
                        Activity didntUnderstandMessage = MessageFactory.text(didntUnderstandMessageText, didntUnderstandMessageText, InputHints.IGNORING_INPUT);
                        stepContext.getContext().sendActivity(didntUnderstandMessage);
                        break;
            }});

        return stepContext.next(null);
    }

    private static CompletableFuture<Void> showWarningForUnsupportedCities(TurnContext turnContext, ObjectNode fromEntities, ObjectNode toEntities) {
        List<String> unsupportedCities = new ArrayList<String>();

        if (StringUtils.isNotEmpty(fromEntities.get("From").toString()) && StringUtils.isEmpty(fromEntities.get("Airport").toString())) {
            unsupportedCities.add(fromEntities.get("From").toString());
        }

        if (StringUtils.isNotEmpty(toEntities.get("To").toString()) && StringUtils.isEmpty(toEntities.get("Airport").toString())) {
            unsupportedCities.add(toEntities.get("To").toString());
        }

        if (!unsupportedCities.isEmpty()) {
            String messageText = String.format("Sorry but the following airports are not supported: %s", String.join(",", unsupportedCities));
            Activity message = MessageFactory.text(messageText, messageText, InputHints.IGNORING_INPUT);
            turnContext.sendActivity(message).thenApply(sendResult -> null);
        }

        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<DialogTurnResult> finalStep(WaterfallStepContext stepContext) {
        // If the child dialog ("BookingDialog") was cancelled, the user failed to confirm or if the intent wasn't BookFlight
        // the Result here will be null.
        if (stepContext.getResult() instanceof BookingDetails) {
            // Now we have all the booking details call the booking service.

            // If the call to the booking service was successful tell the user.

            BookingDetails result = (BookingDetails)stepContext.getResult();
            TimexProperty timexProperty = new TimexProperty(result.getTravelDate());
            String travelDateMsg = timexProperty.toNaturalLanguage(LocalDateTime.now());
            String messageText = String.format("I have you booked to %1s from %2d on %3d", result.getDestination(), result.getOrigin(), travelDateMsg);
            Activity message = MessageFactory.text(messageText, messageText, InputHints.IGNORING_INPUT);
            stepContext.getContext().sendActivity(message).thenApply(sendResult -> null);
        }

        // Restart the main dialog with a different message the second time around
        String promptMessage = "What else can I do for you?";
        return stepContext.replaceDialog(getInitialDialogId(), promptMessage);
    }
}
