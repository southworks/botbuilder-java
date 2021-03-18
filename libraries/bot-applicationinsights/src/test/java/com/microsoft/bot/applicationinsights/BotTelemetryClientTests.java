// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.telemetry.PageViewTelemetry;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import com.microsoft.bot.builder.BotTelemetryClient;
import com.microsoft.bot.builder.Severity;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class BotTelemetryClientTests {
    final private BotTelemetryClient botTelemetryClient;
    final private TelemetryChannel mockTelemetryChannel;

    public BotTelemetryClientTests()
    {
        mockTelemetryChannel = Mockito.mock(TelemetryChannel.class);

        TelemetryConfiguration telemetryConfiguration = new TelemetryConfiguration();
        telemetryConfiguration.setInstrumentationKey("UNITTEST-INSTRUMENTATION-KEY");
        telemetryConfiguration.setChannel(mockTelemetryChannel);
        TelemetryClient telemetryClient = new TelemetryClient(telemetryConfiguration);

        botTelemetryClient = new BotTelemetryClientImpl(telemetryClient);
    }

    @Test
    public void NullTelemetryClientThrows() {
        try {
            new BotTelemetryClientImpl(null);
        } catch (Exception e) {
            Assert.assertEquals("IllegalArgumentException", e.getCause());
        }
    }

    @Test
    public void NonNullTelemetryClientSucceeds() {
        TelemetryClient telemetryClient = new TelemetryClient();

        BotTelemetryClient botTelemetryClient = new BotTelemetryClientImpl(telemetryClient);
    }

    @Test
    public void OverrideTest() {
        TelemetryClient telemetryClient = new TelemetryClient();
        MyBotTelemetryClient botTelemetryClient = new MyBotTelemetryClient(telemetryClient);
    }

    @Test
    public void TrackAvailabilityTest()
    {
        Map<String, String> properties = new HashMap<>();
        Map<String, Double> metrics = new HashMap<>();
        properties.put("hello", "value");
        metrics.put("metric", 0.6);

        botTelemetryClient.trackAvailability(
            "test",
            OffsetDateTime.now(),
            Duration.ofSeconds(1000), // TODO: use computer ticks
            "run location",
            true,
            "message",
            properties,
            metrics);

        Mockito.doAnswer(invocation -> {
            AvailabilityTelemetry eventName = invocation.getArgument(0);

            Assert.assertEquals("test", eventName.getName());
            Assert.assertEquals("value", eventName.getProperties().get("hello"));
            Assert.assertEquals("0.6", eventName.getMetrics().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void TrackEventTest()
    {
        botTelemetryClient.trackEvent("test", new HashMap<String, String>() {{ put("hello", "value"); }}, new HashMap<String, Double>() {{ put("metric", 0.6); }});

        Mockito.doAnswer(invocation -> {
            AvailabilityTelemetry eventName = invocation.getArgument(0);

            Assert.assertEquals("test", eventName.getName());
            Assert.assertEquals("value", eventName.getProperties().get("hello"));
            Assert.assertEquals("0.6", eventName.getMetrics().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void TrackDependencyTest()
    {
        botTelemetryClient.trackDependency("test", "target", "dependencyname", "data", OffsetDateTime.now(), Duration.ofSeconds(1000), "result", false);

        Mockito.doAnswer(invocation -> {
            RemoteDependencyTelemetry eventName = invocation.getArgument(0);

            Assert.assertEquals("test", eventName.getVer());
            Assert.assertEquals("target", eventName.getTarget());
            Assert.assertEquals("dependencyname", eventName.getName());
            // Assert.assertEquals("data", eventName.getData());
            Assert.assertEquals("result", eventName.getResultCode());
            Assert.assertFalse (eventName.getSuccess());

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void TrackExceptionTest()
    {
        Exception expectedException = new Exception("test-exception");

        Mockito.doAnswer(invocation -> {
            ExceptionTelemetry eventName = invocation.getArgument(0);

            Assert.assertEquals(expectedException, eventName.getException());
            Assert.assertEquals("bar", eventName.getProperties().get("foo"));
            Assert.assertEquals("0.6", eventName.getProperties().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void TrackTraceTest()
    {
        botTelemetryClient.trackTrace("hello", Severity.CRITICAL, new HashMap<String, String>() {{ put("foo", "bar"); }});

        Mockito.doAnswer(invocation -> {
            TraceTelemetry eventName = invocation.getArgument(0);

            Assert.assertEquals("hello", eventName.getMessage());
            Assert.assertEquals(Severity.CRITICAL, eventName.getSeverityLevel());
            Assert.assertEquals("bar", eventName.getProperties().get("foo"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void TrackPageViewTest()
    {
        botTelemetryClient.trackDialogView("test", new HashMap<String, String>() {{ put("hello", "value"); }}, new HashMap<String, Double>() {{ put("metric", 0.6); }});

        Mockito.doAnswer(invocation -> {
            PageViewTelemetry eventName = invocation.getArgument(0);

            Assert.assertEquals("test", eventName.getName());
            Assert.assertEquals("value", eventName.getProperties().get("hello"));
            Assert.assertEquals("0.6", eventName.getProperties().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }
}
