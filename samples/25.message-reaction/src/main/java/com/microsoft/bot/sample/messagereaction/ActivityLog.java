// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.messagereaction;

import com.microsoft.bot.builder.Storage;
import com.microsoft.bot.schema.Activity;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ActivityLog {

    private Storage _storage;

    public void ActivityLog(Storage storage) {
        _storage = storage;
    }

    public CompletableFuture<Void> Append(String activityId, Activity activity) throws IllegalAccessException {
        if (activityId == null) {
            throw new IllegalArgumentException("activityId");
        }

        if (activity == null) {
            throw new IllegalArgumentException("activity");
        }

        Map<String, Object> dictionary = new HashMap<String, Object>();
        return _storage.write((Map<String, Object>) dictionary.put(activityId, activity));
    }

    public CompletableFuture<Activity> Find(String activityId) {
        if (activityId == null) {
            throw new IllegalArgumentException("activityId");
        }

        return _storage.read(new String[]{activityId}).thenApply(activitiesResult -> {
        return activitiesResult.size() >= 1 ? ((Activity) activitiesResult.get(activityId)) : null;
        });
    }
}


