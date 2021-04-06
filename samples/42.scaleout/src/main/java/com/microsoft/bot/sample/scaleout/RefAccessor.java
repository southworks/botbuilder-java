// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.scaleout;

import com.microsoft.bot.builder.StatePropertyAccessor;
import com.microsoft.bot.builder.TurnContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * This is an accessor for any object. By definition objects (as opposed to values)
 * are returned by reference in the GetAsync call on the accessor. As such the SetAsync
 * call is never used. The actual act of saving any state to an external store therefore
 * cannot be encapsulated in the Accessor implementation itself. And so to facilitate this
 * the state itself is available as a public property on this class. The reason its here is
 * because the caller of the constructor could pass in null for the state, in which case
 * the factory provided on the GetAsync call will be used.
 */
public class RefAccessor<T extends Class> implements StatePropertyAccessor<T> {
    private T value;

    public RefAccessor(T withValue) {
        value = withValue;
    }

    public T getValue() {
        return value;
    }

    public String getName() {
        return value.getTypeName();
    }

    public CompletableFuture<T> get(TurnContext turnContext, Supplier<T> defaultValueFactory) {
        if (value == null) {
            if (defaultValueFactory == null) {
                throw new IllegalArgumentException("defaultValueFactory cannot be null");
            }

            value = defaultValueFactory.get();
        }

        return CompletableFuture.completedFuture(value);
    }

    @Override
    public CompletableFuture<Void> delete(TurnContext turnContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> set(TurnContext turnContext, T value) {
        throw new UnsupportedOperationException();
    }

    private void setValue(T withValue) {
        this.value = withValue;
    }
}
