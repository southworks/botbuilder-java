// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.RoleTypes;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * When added, this middleware will log incoming and outgoing activities to a
 * TranscriptStore.
 */
public class TranscriptLoggerMiddleware implements Middleware {

    /**
     * The TranscriptLogger to log to.
     */
    private TranscriptLogger transcriptLogger;

    /**
     * Activity queue.
     */
    private Queue<Activity> transcript = new ConcurrentLinkedQueue<Activity>();

    /**
     * Initializes a new instance of the <see cref="TranscriptLoggerMiddleware"/>
     * class.
     *
     * @param withTranscriptLogger The transcript logger to use.
     */
    public TranscriptLoggerMiddleware(TranscriptLogger withTranscriptLogger) {
        if (withTranscriptLogger == null) {
            throw new IllegalArgumentException(
                "TranscriptLoggerMiddleware requires a ITranscriptLogger implementation."
            );
        }

        transcriptLogger = withTranscriptLogger;
    }

    /**
     * Records incoming and outgoing activities to the conversation store.
     *
     * @param context The context object for this turn.
     * @param next    The delegate to call to continue the bot middleware pipeline.
     * @return A task that represents the work queued to execute.
     */
    @Override
    public CompletableFuture<Void> onTurn(TurnContext context, NextDelegate next) {
        // log incoming activity at beginning of turn
        if (context.getActivity() != null) {
            logActivity(cloneActivity(context.getActivity()), true);
        }

        // hook up onSend pipeline
        context.onSendActivities(
            (ctx, activities, nextSend) -> {
                // run full pipeline
                return nextSend.get().thenApply(responses -> {
                    for (Activity activity : activities) {
                        logActivity(cloneActivity(activity), false);
                    }

                    return responses;
                });
            }
        );

        // hook up update activity pipeline
        context.onUpdateActivity(
            (ctx, activity, nextUpdate) -> {
                // run full pipeline
                return nextUpdate.get().thenApply(resourceResponse -> {
                    // add Message Update activity
                    Activity updateActivity = cloneActivity(activity);
                    updateActivity.setType(ActivityTypes.MESSAGE_UPDATE);
                    logActivity(updateActivity, false);

                    return resourceResponse;
                });
            }
        );

        // hook up delete activity pipeline
        context.onDeleteActivity(
            (ctx, reference, nextDel) -> {
                // run full pipeline
                return nextDel.get().thenApply(nextDelResult -> {
                    // add MessageDelete activity
                    // log as MessageDelete activity
                    Activity deleteActivity = new Activity(ActivityTypes.MESSAGE_DELETE) {
                        {
                            setId(reference.getActivityId());
                            applyConversationReference(reference, false);
                        }
                    };

                    logActivity(deleteActivity, false);

                    return null;
                });
            }
        );

        // process bot logic
        return next.next()
            .thenAccept(
                nextResult -> {
                    // flush transcript at end of turn
                    while (!transcript.isEmpty()) {
                        Activity activity = transcript.poll();
                        transcriptLogger.logActivity(activity);
                    }
                }
            );
    }

    private static Activity cloneActivity(Activity activity) {
        ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .findAndRegisterModules();

        try {
            activity = objectMapper.readValue(objectMapper.writeValueAsString(activity), Activity.class);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Activity activityWithId = ensureActivityHasId(activity);

        return activityWithId;
    }

    private static Activity ensureActivityHasId(Activity activity) {
        Activity activityWithId = activity;

        if (activity == null) {
            throw new IllegalArgumentException("Cannot check or add Id on a null Activity.");
        }

        if (activity.getId() == null) {
            String generatedId = String.format("g_%s", UUID.randomUUID().toString());
            activity.setId(generatedId);
        }

        return activityWithId;
    }

    private void logActivity(Activity activity, boolean incoming) {
        if (activity.getTimestamp() == null) {
            activity.setTimestamp(OffsetDateTime.now(ZoneId.of("UTC")));
        }

        if (activity.getFrom() == null) {
            activity.setFrom(new ChannelAccount());
        }

        if (activity.getFrom().getRole() == null) {
            activity.getFrom().setRole(incoming ? RoleTypes.USER : RoleTypes.BOT);
        }

        transcript.offer(activity);
    }
}
