// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.core;

import com.codepoetics.protonpack.collectors.CompletableFutures;
import com.microsoft.applicationinsights.core.dependencies.apachecommons.io.IOUtils;
import com.microsoft.applicationinsights.core.dependencies.apachecommons.lang3.StringUtils;
import com.microsoft.bot.builder.*;
import com.microsoft.bot.dialogs.Dialog;
import com.microsoft.bot.schema.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DialogAndWelcomeBot<T extends Dialog> extends DialogBot {
    /**
     * Creates a DialogBot.
     * @param withConversationState ConversationState to use in the bot
     * @param withUserState         UserState to use
     * @param withDialog            Param inheriting from Dialog class
     */
    public DialogAndWelcomeBot(ConversationState withConversationState, UserState withUserState, T withDialog) {
        super(withConversationState, withUserState, withDialog);
    }

    @Override
    protected CompletableFuture<Void> onMembersAdded(List<ChannelAccount> membersAdded, TurnContext turnContext) {
        return turnContext.getActivity().getMembersAdded().stream()
            .filter(member -> !StringUtils
                .equals(member.getId(), turnContext.getActivity().getRecipient().getId()))
            .map(channel -> {
                // Greet anyone that was not the target (recipient) of this message.
                // To learn more about Adaptive Cards, see https://aka.ms/msbot-adaptivecards for more details.
                Attachment welcomeCard = createAdaptiveCardAttachment();
                Activity response = MessageFactory.attachment(welcomeCard,null,"Welcome to Bot Framework!",null);

                return turnContext.sendActivity(response).thenApply(sendResult -> {
                    return Dialog.run(getDialog(), turnContext, getConversationState().createProperty("DialogState"));
                });
            })
            .collect(CompletableFutures.toFutureList())
            .thenApply(resourceResponse -> null);
    }

    // Load attachment from embedded resource.
    private Attachment createAdaptiveCardAttachment()
    {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("cards/welcomeCard.json"))
        {
            String adaptiveCardJson = IOUtils.toString(inputStream, StandardCharsets.UTF_8.toString());

                return new Attachment() {{
                    setContentType("application/vnd.microsoft.card.adaptive");
                    setContent(Serialization.jsonToTree(adaptiveCardJson));
                }};

        }
        catch (IOException e) {
            e.printStackTrace();
            return new Attachment();
        }
    }
}
