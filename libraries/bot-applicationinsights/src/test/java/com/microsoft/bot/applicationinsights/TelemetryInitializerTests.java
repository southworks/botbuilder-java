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
    public void telemetryInitializerStoresActivity() {
        TelemetryClient telemetryClient = new TelemetryClient();

        TelemetryLoggerMiddleware telemetryLoggerMiddleware = new TelemetryLoggerMiddleware((BotTelemetryClient) telemetryClient, false);
        TelemetryInitializerMiddleware initializerMiddleware = new TelemetryInitializerMiddleware(telemetryLoggerMiddleware, false);

        /***/
    }

    @Test
    public void telemetryInitializerMiddlewareLogActivitiesWhenEnabled() {
    	TelemetryClient mockTelemetryClient = Mockito.mock(TelemetryClient.class);

        TelemetryLoggerMiddleware telemetryLoggerMiddleware = new TelemetryLoggerMiddleware((BotTelemetryClient) mockTelemetryClient, false);

        TestAdapter testAdapter = new TestAdapter()
        		.use(new TelemetryInitializerMiddleware(telemetryLoggerMiddleware, true));
                
        TestFlow testFlow = new TestFlow(testAdapter, (turnContext) -> {
            Activity typingActivity = new Activity() {
            {
            	setType(ActivityTypes.TYPING);
            	setRelatesTo(turnContext.getActivity().getRelatesTo());
            }};
            turnContext.sendActivity(typingActivity).join();
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                // Empty error
            }
            turnContext.sendActivity(String.format("echo:%s", turnContext.getActivity().getText())).join();
            return CompletableFuture.completedFuture(null);
        }); 
        
        testFlow.send("foo")
		        .assertReply(activity -> {
		            Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
		        })
		        .assertReply("echo:foo");
                
        verify(mockTelemetryClient, times(1));
    }

    @Test
    public void telemetryInitializerMiddlewareNotLogActivitiesWhenDisabled() {
    	TelemetryClient mockTelemetryClient = Mockito.mock(TelemetryClient.class);

        TelemetryLoggerMiddleware telemetryLoggerMiddleware = new TelemetryLoggerMiddleware((BotTelemetryClient) mockTelemetryClient, false);

        TestAdapter testAdapter = new TestAdapter()
        		.use(new TelemetryInitializerMiddleware(telemetryLoggerMiddleware, false));
                
        TestFlow testFlow = new TestFlow(testAdapter, (turnContext) -> {
            Activity typingActivity = new Activity() {
            {
            	setType(ActivityTypes.TYPING);
            	setRelatesTo(turnContext.getActivity().getRelatesTo());
            }};
            turnContext.sendActivity(typingActivity).join();
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                // Empty error
            }
            turnContext.sendActivity(String.format("echo:%s", turnContext.getActivity().getText())).join();
            return CompletableFuture.completedFuture(null);
        }); 
        
        testFlow.send("foo")
		        .assertReply(activity -> {
		            Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
		        })
		        .assertReply("echo:foo");
                
        verify(mockTelemetryClient, times(0));
    }
}
