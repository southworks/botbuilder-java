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
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class BotTelemetryClientTests {
    
    private BotTelemetryClient botTelemetryClient;
    private TelemetryChannel mockTelemetryChannel;

    @Before
    public void initialize() {
        mockTelemetryChannel = Mockito.mock(TelemetryChannel.class);

        TelemetryConfiguration telemetryConfiguration = new TelemetryConfiguration();
        telemetryConfiguration.setInstrumentationKey("UNITTEST-INSTRUMENTATION-KEY");
        telemetryConfiguration.setChannel(mockTelemetryChannel);
        TelemetryClient telemetryClient = new TelemetryClient(telemetryConfiguration);

        botTelemetryClient = new BotTelemetryClientImpl(telemetryClient);
    }

    @Test
    public void nullTelemetryClientThrows() {
        Assert.assertThrows(IllegalArgumentException.class, () -> { 
            new BotTelemetryClientImpl(null); 
        });
    }

    @Test
    public void nonNullTelemetryClientSucceeds() {
        TelemetryClient telemetryClient = new TelemetryClient();

        BotTelemetryClient botTelemetryClient = new BotTelemetryClientImpl(telemetryClient);
    }

    @Test
    public void overrideTest() {
        TelemetryClient telemetryClient = new TelemetryClient();
        MyBotTelemetryClient botTelemetryClient = new MyBotTelemetryClient(telemetryClient);
    }

    @Test
    public void trackAvailabilityTest() {
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
            AvailabilityTelemetry availabilityTelemetry = invocation.getArgument(0);

            Assert.assertEquals("test", availabilityTelemetry.getName());
            Assert.assertEquals("value", availabilityTelemetry.getProperties().get("hello"));
            Assert.assertEquals((Double) 0.6, availabilityTelemetry.getMetrics().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void trackEventTest() {
        Map<String, String> properties = new HashMap<>();
        properties.put("hello", "value");
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("metric", 0.6);
        
        botTelemetryClient.trackEvent("test", properties, metrics);

        Mockito.doAnswer(invocation -> {
            AvailabilityTelemetry availabilityTelemetry = invocation.getArgument(0);

            Assert.assertEquals("test", availabilityTelemetry.getName());
            Assert.assertEquals("value", availabilityTelemetry.getProperties().get("hello"));
            Assert.assertEquals((Double) 0.6, availabilityTelemetry.getMetrics().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void trackDependencyTest() {
        botTelemetryClient.trackDependency(
                "test", 
                "target", 
                "dependencyname", 
                "data", 
                OffsetDateTime.now(), 
                Duration.ofSeconds(1000), // TODO: use computer ticks
                "result", false);

        Mockito.doAnswer(invocation -> {
            RemoteDependencyTelemetry remoteDependencyTelemetry = invocation.getArgument(0);

            Assert.assertEquals("test", remoteDependencyTelemetry.getType());
            Assert.assertEquals("target", remoteDependencyTelemetry.getTarget());
            Assert.assertEquals("dependencyname", remoteDependencyTelemetry.getName());
            Assert.assertEquals("result", remoteDependencyTelemetry.getResultCode());
            Assert.assertFalse(remoteDependencyTelemetry.getSuccess());

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void trackExceptionTest() {
        Exception expectedException = new Exception("test-exception");

        Mockito.doAnswer(invocation -> {
            ExceptionTelemetry exceptionTelemetry = invocation.getArgument(0);

            Assert.assertEquals(expectedException, exceptionTelemetry.getException());
            Assert.assertEquals("bar", exceptionTelemetry.getProperties().get("foo"));
            Assert.assertEquals(0.6, exceptionTelemetry.getProperties().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void trackTraceTest() {
        Map<String, String> properties = new HashMap<>();
        properties.put("foo", "bar");
        
        botTelemetryClient.trackTrace("hello", Severity.CRITICAL, properties);

        Mockito.doAnswer(invocation -> {
            TraceTelemetry traceTelemetry = invocation.getArgument(0);

            Assert.assertEquals("hello", traceTelemetry.getMessage());
            Assert.assertEquals(Severity.CRITICAL, traceTelemetry.getSeverityLevel());
            Assert.assertEquals("bar", traceTelemetry.getProperties().get("foo"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }

    @Test
    public void trackPageViewTest() {
        Map<String, String> properties = new HashMap<>();
        properties.put("hello", "value");
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("metric", 0.6);
        
        botTelemetryClient.trackDialogView("test", properties, metrics);

        Mockito.doAnswer(invocation -> {
            PageViewTelemetry pageViewTelemetry = invocation.getArgument(0);

            Assert.assertEquals("test", pageViewTelemetry.getName());
            Assert.assertEquals("value", pageViewTelemetry.getProperties().get("hello"));
            Assert.assertEquals((Double) 0.6, pageViewTelemetry.getProperties().get("metric"));

            return null;
        }).when(mockTelemetryChannel).send(Mockito.any(AvailabilityTelemetry.class));
    }
}
