// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.microsoft.bot.dialogs.prompts;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.Attachment;

/**
 * Prompts a user to upload attachments, like images.
 */
public class AttachmentPrompt extends Prompt<List<Attachment>> {
    /**
     * Initializes a new instance of the AttachmentPrompt class.
     * @param dialogId The ID to assign to this prompt.
     * @param validator Optional, a {@link PromptValidator{T}} that contains additional,
     * custom validation for this prompt.
     * The value of dialogId must be unique within the
     * {@link DialogSet} or {@link ComponentDialog} to which the prompt is added.
     */
    public AttachmentPrompt(String dialogId, PromptValidator<List<Attachment>> validator) {
        super(dialogId, validator);
    }

    /**
     * Prompts the user for input.
     * @param turnContext Context for the current turn of conversation with the user.
     * @param state Contains state for the current instance of the prompt on the dialog stack.
     * @param options A prompt options object constructed from the options initially provided
     * in the call to {@link DialogContext#prompt(String, PromptOptions)}.
     * @param isRetry true if this is the first time this prompt dialog instance
     * on the stack is prompting the user for input; otherwise, false.
     * @return A task representing the asynchronous operation.
     */
    @Override
    protected CompletableFuture<Void> onPrompt(TurnContext turnContext, Map<String, Object> state, PromptOptions options, Boolean isRetry) {
        if (turnContext == null) {
            throw new IllegalArgumentException("turnContext");
        }

        if (options == null) {
            throw new IllegalArgumentException("options");
        }

        CompletableFuture<Void> task = null;

        if (isRetry && options.getRetryPrompt() != null) {
            task = turnContext.sendActivity(options.getRetryPrompt());
        } else if (options.prompt != null) {
            task = turnContext.sendActivity(options.getPrompt());
        }

        return task == null ? CompletableFuture.completedFuture(null) : task;
    }

    /**
     * Attempts to recognize the user's input.
     * @param turnContext Context for the current turn of conversation with the user.
     * @param state Contains state for the current instance of the prompt on the dialog stack.
     * @param options A prompt options object constructed from the options initially provided
     * in the call to {@link DialogContext#prompt(String, PromptOptions)}.
     * @return A task representing the asynchronous operation.
     * If the task is successful, the result describes the result of the recognition attempt.
     */
    protected CompletableFuture<PromptRecognizerResult<List<Attachment>>> onRecognize(TurnContext turnContext, Map<String, Object> state, PromptOptions options) {
        if (turnContext == null) {
            throw new IllegalArgumentException("turnContext");
        }

        PromptRecognizerResult<List<Attachment>> result = new PromptRecognizerResult<List<Attachment>>();
        if (turnContext.getActivity().getType() == ActivityTypes.MESSAGE) {
            Activity message = turnContext.getActivity().asMessageActivity();
            if (message.getAttachments() != null && message.getAttachments().size() > 0) {
                result.setSucceeded(true);
                result.setValue(message.getAttachments());
            }
        }

        return CompletableFuture.completedFuture(result);
    }
}
