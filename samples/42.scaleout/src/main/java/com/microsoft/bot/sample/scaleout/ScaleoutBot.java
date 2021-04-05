// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.scaleout;

import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.dialogs.Dialog;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a bot that processes incoming Activities.
 * For each user interaction, an instance of this class is created and the OnTurnAsync method is called.
 */
public class ScaleoutBot <T extends Dialog> extends ActivityHandler {

    private final Store store;
    private final Dialog dialog;

    /**
     * Initializes a new instance of the {@link ScaleoutBot} class.
     * @param withStore The store we will be using.
     * @param withDialog The root dialog to run.
     */
    public ScaleoutBot(Store withStore, T withDialog) {
        if (withStore == null) {
            throw new IllegalArgumentException("withStore can't be null");
        }
        store = withStore;

        if (withDialog == null) {
            throw new IllegalArgumentException("withDialog can't be null");
        }
        dialog = withDialog;
    }

    @Override
    protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
        // Create the storage key for this conversation.
        String key = String.format(
            "%s/conversations/%s",
            turnContext.getActivity().getChannelId(),
            turnContext.getActivity().getConversation().getId());

        // The execution sits in a loop because there might be a retry if the save operation fails.
        while (true) {
            // Load any existing state associated with this key
            store.load(key)
                .thenCompose(pairOldState -> {
                    // Run the dialog system with the old state and inbound activity,
                    // the result is a new state and outbound activities.
                    return DialogHost.run(dialog, turnContext.getActivity(), pairOldState.getLeft())
                        .thenCompose(pairNewState -> {
                            // Save the updated state associated with this key.
                            return store.save(key, pairNewState.getRight(), pairOldState.getRight())
                                .thenCompose(success -> {
                                    // Following a successful save, send any outbound Activities,
                                    // otherwise retry everything.
                                    if (success) {
                                        if (!pairNewState.getLeft().isEmpty()) {
                                            // This is an actual send on the TurnContext we were given
                                            // and so will actual do a send this time.
                                            return turnContext.sendActivities(pairNewState.getLeft())
                                                .thenApply(result -> null);
                                        }
                                    }
                                    return null;
                                });
                        });
                });
        }
    }
}
