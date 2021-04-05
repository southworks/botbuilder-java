// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.scaleout;

import com.microsoft.bot.schema.Pair;

import java.util.concurrent.CompletableFuture;

/**
 * An ETag aware store definition.
 * The interface is defined in terms of Object to move serialization out of the storage layer
 * while still indicating it is JSON, a fact the store may choose to make use of.
 */
public interface Store {

    CompletableFuture<Pair<Object, String>> load(String key);

    CompletableFuture<Boolean> save(String key, Object content, String etag);
}
