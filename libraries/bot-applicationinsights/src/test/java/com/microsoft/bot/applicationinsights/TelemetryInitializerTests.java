// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.bot.applicationinsights.core.TelemetryInitializerMiddleware;
import com.microsoft.bot.builder.BotTelemetryClient;
import com.microsoft.bot.builder.TelemetryLoggerMiddleware;
import com.microsoft.bot.builder.adapters.TestAdapter;
import com.microsoft.bot.builder.adapters.TestFlow;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;

public class TelemetryInitializerTests {

    @Test
    public void telemetryInitializerMiddlewareLogActivitiesEnabled() {

        // Arrange
        TelemetryClient mockTelemetryClient = Mockito.mock(BotTelemetryClient.class);
        TelemetryLoggerMiddleware telemetryLoggerMiddleware = new TelemetryLoggerMiddleware(mockTelemetryClient, false);

        TestAdapter testAdapter = new TestAdapter()
        		.use(new TelemetryInitializerMiddleware(telemetryLoggerMiddleware, true));

        // Act
        // Default case logging Send/Receive Activities
        TestFlow testFlow = new TestFlow(testAdapter, turnContext -> {
            Activity typingActivity = new Activity(ActivityTypes.TYPING);
            typingActivity.setRelatesTo(turnContext.getActivity().getRelatesTo());

            turnContext.sendActivity(typingActivity).join();
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                // Empty error
            }
            turnContext.sendActivity(String.format("echo:%s", turnContext.getActivity().getText())).join();
            return CompletableFuture.completedFuture(null);
        })
        .send("foo")
            .assertReply(activity -> {
                Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
            })
            .assertReply("echo:foo")
        .send("bar")
            .assertReply(activity -> {
                Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
            })
            .assertReply("echo:bar")
        .startTest().join();

        // Verify
        verify(mockTelemetryClient, times(6));
    }

    @Test
    public void telemetryInitializerMiddlewareNotLogActivitiesDisabled() {

        // Arrange
    	TelemetryClient mockTelemetryClient = Mockito.mock(BotTelemetryClient.class);
        TelemetryLoggerMiddleware telemetryLoggerMiddleware = new TelemetryLoggerMiddleware(mockTelemetryClient, false);

        TestAdapter testAdapter = new TestAdapter()
        		.use(new TelemetryInitializerMiddleware(telemetryLoggerMiddleware, false));

        // Act
        // Default case logging Send/Receive Activities
        TestFlow testFlow = new TestFlow(testAdapter, (turnContext) -> {
            Activity typingActivity = new Activity(ActivityTypes.TYPING);
            typingActivity.setRelatesTo(turnContext.getActivity().getRelatesTo());

            turnContext.sendActivity(typingActivity).join();
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                // Empty error
            }
            turnContext.sendActivity(String.format("echo:%s", turnContext.getActivity().getText())).join();
            return CompletableFuture.completedFuture(null);
        })
        .send("foo")
            .assertReply(activity -> {
                Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
            })
            .assertReply("echo:foo")
        .send("bar")
            .assertReply(activity -> {
                Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
            })
            .assertReply("echo:bar")
        .startTest().join();

        // Verify
        verify(mockTelemetryClient, times(0));
    }
}
