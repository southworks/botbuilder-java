// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights.core;

import com.microsoft.applicationinsights.core.dependencies.http.client.protocol.HttpClientContext;
import com.microsoft.applicationinsights.core.dependencies.http.protocol.HttpContext;
import com.microsoft.bot.builder.BotAssert;
import com.microsoft.bot.builder.Middleware;
import com.microsoft.bot.builder.NextDelegate;
import com.microsoft.bot.builder.TelemetryLoggerMiddleware;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;

import java.util.concurrent.CompletableFuture;

public class TelemetryInitializerMiddleware implements Middleware {
    private HttpContext httpContext;
    private TelemetryLoggerMiddleware telemetryLoggerMiddleware;
    private Boolean logActivityTelemetry;

    public TelemetryInitializerMiddleware(TelemetryLoggerMiddleware withTelemetryLoggerMiddleware, Boolean withLogActivityTelemetry) {
        telemetryLoggerMiddleware = withTelemetryLoggerMiddleware;
        logActivityTelemetry = withLogActivityTelemetry != null ? withLogActivityTelemetry : true;
    }

    public CompletableFuture<Void> onTurn(TurnContext context, NextDelegate next) {
        BotAssert.contextNotNull(context);

        if (context.getActivity() != null) {
            Activity activity = context.getActivity();

            if (this.httpContext == null) {
                this.httpContext = HttpClientContext.create();
            }

            Object item = httpContext.getAttribute(TelemetryBotIdInitializer.BotActivityKey);

            if (item != null) {
                httpContext.removeAttribute(TelemetryBotIdInitializer.BotActivityKey);
            }

            httpContext.setAttribute(TelemetryBotIdInitializer.BotActivityKey, activity);
        }

        if (logActivityTelemetry) {
            return telemetryLoggerMiddleware.onTurn(context, next);
        } else {
            return next.next();
        }

        return CompletableFuture.completedFuture(null);
    }
}

