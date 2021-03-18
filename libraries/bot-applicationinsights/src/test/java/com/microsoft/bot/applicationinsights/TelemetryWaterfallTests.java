// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.bot.builder.AutoSaveStateMiddleware;
import com.microsoft.bot.builder.BotTelemetryClient;
import com.microsoft.bot.builder.Severity;
import com.microsoft.bot.builder.StatePropertyAccessor;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.adapters.TestAdapter;
import com.microsoft.bot.builder.ConversationState;
import com.microsoft.bot.builder.MemoryStorage;
import com.microsoft.bot.builder.adapters.TestFlow;
import com.microsoft.bot.dialogs.Dialog;
import com.microsoft.bot.dialogs.DialogContext;
import com.microsoft.bot.dialogs.DialogInstance;
import com.microsoft.bot.dialogs.DialogReason;
import com.microsoft.bot.dialogs.DialogSet;
import com.microsoft.bot.dialogs.DialogState;
import com.microsoft.bot.dialogs.WaterfallDialog;
import com.microsoft.bot.dialogs.WaterfallStep;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TelemetryWaterfallTests {

    @Captor
    ArgumentCaptor<String> eventNameCaptor = ArgumentCaptor.forClass(String.class);

    @Captor
    ArgumentCaptor<Map<String, String>> propertiesCaptor = ArgumentCaptor.forClass(Map.class);

    @Captor
    ArgumentCaptor<Map<String, Double>> metricsCaptor = ArgumentCaptor.forClass(Map.class);

    @Test
    public void waterfall() {
        ConversationState convoState = new ConversationState(new MemoryStorage());
        TestAdapter adapter = new TestAdapter(TestAdapter.createConversationReference("Waterfall", "User1", "Bot"))
            .use(new AutoSaveStateMiddleware(convoState));

        BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);
        StatePropertyAccessor<DialogState> dialogState = convoState.createProperty("dialogState");
        DialogSet dialogs = new DialogSet(dialogState);

        dialogs.add(new WaterfallDialog("test", newWaterfall()));
        dialogs.setTelemetryClient(telemetryClient);

        new TestFlow(adapter, turnContext -> {
            DialogContext dc = dialogs.createContext(turnContext).join();
            dc.continueDialog().join();
            if (!turnContext.getResponded()) {
                dc.beginDialog("test", null).join();
            }
            return CompletableFuture.completedFuture(null);
        })
        .send("hello")
        .assertReply("step1")
        .send("hello")
        .assertReply("step2")
        .send("hello")
        .assertReply("step3")
        .startTest()
        .join();

        // C#'s trackEvent method of BotTelemetryClient has nullable parameters,
        // therefore it always calls the same method.
        // On the other hand, Java's BotTelemetryClient overloads the trackEvent method,
        // so instead of calling the same method, it calls a method with less parameters.
        // In this particular test, WaterfallDialog's beginDialog calls the method with only two parameters
        Mockito.verify(telemetryClient, Mockito.times(4)).trackEvent(
            Mockito.anyString(),
            Mockito.anyMap()
        );
        System.out.printf("Complete");
    }

    @Test
    public void waterfallWithCallback() {
        ConversationState convoState = new ConversationState(new MemoryStorage());
        TestAdapter adapter = new TestAdapter(TestAdapter.createConversationReference("WaterfallWithCallback", "User1", "Bot"))
            .use(new AutoSaveStateMiddleware(convoState));

        BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);
        StatePropertyAccessor<DialogState> dialogState = convoState.createProperty("dialogState");
        DialogSet dialogs = new DialogSet(dialogState);
        WaterfallDialog waterfallDialog = new WaterfallDialog("test", newWaterfall());

        dialogs.add(waterfallDialog);
        dialogs.setTelemetryClient(telemetryClient);

        new TestFlow(adapter, turnContext -> {
            DialogContext dc = dialogs.createContext(turnContext).join();
            dc.continueDialog().join();
            if (!turnContext.getResponded()) {
                dc.beginDialog("test", null).join();
            }
            return CompletableFuture.completedFuture(null);
        })
        .send("hello")
        .assertReply("step1")
        .send("hello")
        .assertReply("step2")
        .send("hello")
        .assertReply("step3")
        .startTest()
        .join();

        // C#'s trackEvent method of BotTelemetryClient has nullable parameters,
        // therefore it always calls the same method.
        // On the other hand, Java's BotTelemetryClient overloads the trackEvent method,
        // so instead of calling the same method, it calls a method with less parameters.
        // In this particular test, WaterfallDialog's beginDialog calls the method with only two parameters
        Mockito.verify(telemetryClient, Mockito.times(4)).trackEvent(
            Mockito.anyString(),
            Mockito.anyMap()
        );
    }

    @Test
    public void waterfallWithActionsNull() {
        BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);
        WaterfallDialog waterfall = new WaterfallDialog("test", null);
        waterfall.setTelemetryClient(telemetryClient);
        Assert.assertThrows(IllegalArgumentException.class, () -> waterfall.addStep(null));
    }

    @Test
    public void ensureEndDialogCalled() {
        ConversationState convoState = new ConversationState(new MemoryStorage());
        TestAdapter adapter = new TestAdapter(TestAdapter.createConversationReference("EnsureEndDialogCalled", "User1", "Bot"))
            .use(new AutoSaveStateMiddleware(convoState));

        StatePropertyAccessor<DialogState> dialogState = convoState.createProperty("dialogState");
        DialogSet dialogs = new DialogSet(dialogState);
        HashMap<String, Map<String, String>> saved_properties = new HashMap<>();
        final Integer[] counter = {0};

        // Set up the client to save all logged property names and associated properties (in "saved_properties").
        BotTelemetryClient telemetryClient = new BotTelemetryClient() {
            @Override
            public void trackAvailability(String name, OffsetDateTime timeStamp, Duration duration, String runLocation, boolean success, String message, Map<String, String> properties, Map<String, Double> metrics) {

            }

            @Override
            public void trackDependency(String dependencyTypeName, String target, String dependencyName, String data, OffsetDateTime startTime, Duration duration, String resultCode, boolean success) {

            }

            @Override
            public void trackEvent(String eventName, Map<String, String> properties, Map<String, Double> metrics) {
                StringBuilder sb = new StringBuilder(eventName).append("_").append(counter[0]++);
                saved_properties.put(sb.toString(), properties);
            }

            @Override
            public void trackException(Exception exception, Map<String, String> properties, Map<String, Double> metrics) {

            }

            @Override
            public void trackTrace(String message, Severity severityLevel, Map<String, String> properties) {

            }

            @Override
            public void trackDialogView(String dialogName, Map<String, String> properties, Map<String, Double> metrics) {

            }

            @Override
            public void flush() {

            }
        };

        MyWaterfallDialog waterfallDialog = new MyWaterfallDialog("test", newWaterfall());

        dialogs.add(waterfallDialog);
        dialogs.setTelemetryClient(telemetryClient);

        new TestFlow(adapter, turnContext -> {
            DialogContext dc = dialogs.createContext(turnContext).join();
            dc.continueDialog().join();
            if (!turnContext.getResponded()) {
                dc.beginDialog("test", null).join();
            }
            return CompletableFuture.completedFuture(null);
        })
        .send("hello")
        .assertReply("step1")
        .send("hello")
        .assertReply("step2")
        .send("hello")
        .assertReply("step3")
        .send("hello")
        .assertReply("step1")
        .startTest()
        .join();

        Assert.assertEquals((Integer)7, counter[0]);

        // Verify:
        // Event name is "WaterfallComplete"
        // Event occurs on the 4th event logged
        // Event contains DialogId
        // Event DialogId is set correctly.
        Assert.assertTrue(saved_properties.get("WaterfallComplete_4").containsKey("DialogId"));
        Assert.assertEquals("test", saved_properties.get("WaterfallComplete_4").get("DialogId"));
        Assert.assertTrue(saved_properties.get("WaterfallComplete_4").containsKey("InstanceId"));
        Assert.assertTrue(saved_properties.get("WaterfallStep_1").containsKey("InstanceId"));

        // Verify naming on lambda's is "StepXofY"
        Assert.assertTrue(saved_properties.get("WaterfallStep_1").containsKey("StepName"));
        Assert.assertEquals("Step1of3", saved_properties.get("WaterfallStep_1").get("StepName"));
        Assert.assertTrue(waterfallDialog.getEndDialogCalled());
    }

    @Test
    public void ensureCancelDialogCalled() {
        ConversationState convoState = new ConversationState(new MemoryStorage());
        TestAdapter adapter = new TestAdapter(TestAdapter.createConversationReference("EnsureCancelDialogCalled", "User1", "Bot"))
            .use(new AutoSaveStateMiddleware(convoState));

        StatePropertyAccessor<DialogState> dialogState = convoState.createProperty("dialogState");
        DialogSet dialogs = new DialogSet(dialogState);
        HashMap<String, Map<String, String>> saved_properties = new HashMap<>();
        final Integer[] counter = {0};

        // Set up the client to save all logged property names and associated properties (in "saved_properties").
        BotTelemetryClient telemetryClient = new BotTelemetryClient() {
            @Override
            public void trackAvailability(String name, OffsetDateTime timeStamp, Duration duration, String runLocation, boolean success, String message, Map<String, String> properties, Map<String, Double> metrics) {

            }

            @Override
            public void trackDependency(String dependencyTypeName, String target, String dependencyName, String data, OffsetDateTime startTime, Duration duration, String resultCode, boolean success) {

            }

            @Override
            public void trackEvent(String eventName, Map<String, String> properties, Map<String, Double> metrics) {
                StringBuilder sb = new StringBuilder(eventName).append("_").append(counter[0]++);
                saved_properties.put(sb.toString(), properties);
            }

            @Override
            public void trackException(Exception exception, Map<String, String> properties, Map<String, Double> metrics) {

            }

            @Override
            public void trackTrace(String message, Severity severityLevel, Map<String, String> properties) {

            }

            @Override
            public void trackDialogView(String dialogName, Map<String, String> properties, Map<String, Double> metrics) {

            }

            @Override
            public void flush() {

            }
        };

        List<WaterfallStep> steps = new ArrayList<>();
        steps.add(step -> {
            step.getContext().sendActivity("step1").join();
            return CompletableFuture.completedFuture(Dialog.END_OF_TURN);
        });
        steps.add(step -> {
            step.getContext().sendActivity("step2").join();
            return CompletableFuture.completedFuture(Dialog.END_OF_TURN);
        });
        steps.add(step -> {
            step.cancelAllDialogs().join();
            return CompletableFuture.completedFuture(Dialog.END_OF_TURN);
        });

        MyWaterfallDialog waterfallDialog = new MyWaterfallDialog("test", steps);

        dialogs.add(waterfallDialog);
        dialogs.setTelemetryClient(telemetryClient);

        new TestFlow(adapter, turnContext -> {
            DialogContext dc = dialogs.createContext(turnContext).join();
            dc.continueDialog().join();
            if (!turnContext.getResponded()) {
                dc.beginDialog("test", null).join();
            }
            return CompletableFuture.completedFuture(null);
        })
            .send("hello")
            .assertReply("step1")
            .send("hello")
            .assertReply("step2")
            .send("hello")
            .assertReply("step1")
            .startTest()
            .join();

        Assert.assertEquals((Integer)7, counter[0]);

        // Verify:
        // Event name is "WaterfallCancel"
        // Event occurs on the 4th event logged
        // Event contains DialogId
        // Event DialogId is set correctly.
        Assert.assertTrue(saved_properties.get("WaterfallStart_0").containsKey("DialogId"));
        Assert.assertTrue(saved_properties.get("WaterfallStart_0").containsKey("InstanceId"));
        Assert.assertTrue(saved_properties.get("WaterfallCancel_4").containsKey("DialogId"));
        Assert.assertEquals("test", saved_properties.get("WaterfallCancel_4").get("DialogId"));
        Assert.assertTrue(saved_properties.get("WaterfallCancel_4").containsKey("StepName"));
        Assert.assertTrue(saved_properties.get("WaterfallCancel_4").containsKey("InstanceId"));

        // Event contains "StepName"
        // Event naming on lambda's is "StepXofY"
        Assert.assertEquals("Step3of3", saved_properties.get("WaterfallCancel_4").get("StepName"));
        Assert.assertTrue(waterfallDialog.getCancelDialogCalled());
        Assert.assertFalse(waterfallDialog.getEndDialogCalled());
    }

    private static List<WaterfallStep> newWaterfall() {
        List<WaterfallStep> waterfall = new ArrayList<>();

        waterfall.add(step -> {
            step.getContext().sendActivity("step1").join();
            return CompletableFuture.completedFuture(Dialog.END_OF_TURN);
        });

        waterfall.add(step -> {
            step.getContext().sendActivity("step2").join();
            return CompletableFuture.completedFuture(Dialog.END_OF_TURN);
        });

        waterfall.add(step -> {
            step.getContext().sendActivity("step3").join();
            return CompletableFuture.completedFuture(Dialog.END_OF_TURN);
        });

        return waterfall;
    }

    private class MyWaterfallDialog extends WaterfallDialog {
        private Boolean endDialogCalled;
        private Boolean cancelDialogCalled;

        public MyWaterfallDialog(String id, List<WaterfallStep> actions) {
            super(id, actions);

            endDialogCalled = false;
            cancelDialogCalled = false;
        }

        @Override
        public CompletableFuture<Void> endDialog(TurnContext turnContext, DialogInstance instance, DialogReason reason) {
            if (reason == DialogReason.END_CALLED) {
                endDialogCalled = true;
            } else if (reason == DialogReason.CANCEL_CALLED) {
                cancelDialogCalled = true;
            }

            return super.endDialog(turnContext, instance, reason);
        }

        public Boolean getEndDialogCalled() {
            return endDialogCalled;
        }

        public Boolean getCancelDialogCalled() {
            return cancelDialogCalled;
        }
    }
}
