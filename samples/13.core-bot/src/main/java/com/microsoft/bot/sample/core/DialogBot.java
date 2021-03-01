// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.core;

import com.microsoft.bot.builder.*;
import com.microsoft.bot.dialogs.Dialog;

import java.util.concurrent.CompletableFuture;

// This IBot implementation can run any type of Dialog. The use of type parameterization is to allows multiple different bots
// to be run at different endpoints within the same project...
public class DialogBot<T extends Dialog> extends ActivityHandler {
    protected Dialog dialog;
    protected BotState conversationState;
    protected BotState userState;

    public DialogBot(ConversationState conversationState, UserState userState, T dialog) {
        this.conversationState = conversationState;
        this.userState = userState;
        this.dialog = dialog;
    }

    @Override
    public CompletableFuture<Void> onTurn(TurnContext turnContext) {
        super.onTurn(turnContext).thenCompose(response ->
                // Save any state changes that might have occurred during the turn.
                conversationState.saveChanges(turnContext, false).thenCompose(saveResult ->
                    userState.saveChanges(turnContext, false).thenCompose(userSaveResult -> null)
                )
            );

        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
        // Run the Dialog with the new message Activity.
        return Dialog.run(dialog, turnContext, conversationState.createProperty("DialogState"));
    }
}
