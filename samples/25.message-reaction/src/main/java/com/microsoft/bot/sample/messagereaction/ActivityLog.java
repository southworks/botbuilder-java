// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.messagereaction;

import com.microsoft.bot.builder.Storage;
import com.microsoft.bot.schema.Activity;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ActivityLog {

    private Storage storage;

    public ActivityLog(Storage withStorage) {
        storage = withStorage;
    }

    public CompletableFuture<Void> append(String activityId, Activity activity) {
        if (activityId == null) {
            throw new IllegalArgumentException("activityId");
        }

        if (activity == null) {
            throw new IllegalArgumentException("activity");
        }

        Map<String, Object> dictionary = new HashMap<String, Object>();
        dictionary.put(activityId, activity);
        return storage.write((Map<String, Object>) dictionary);
    }

    public CompletableFuture<Activity> find(String activityId) {
        if (activityId == null) {
            throw new IllegalArgumentException("activityId");
        }

        return storage.read(new String[]{activityId}).thenApply(activitiesResult -> activitiesResult.size() >= 1 ? ((Activity) activitiesResult.get(activityId)) : null);
    }
}
