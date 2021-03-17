// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.bot.builder.BotTelemetryClient;
import com.microsoft.bot.builder.Severity;
import org.junit.Assert;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;

public class BotTelemetryCientTests {
    public class ConstructorTests {
        public void NullTelemetryClientThrows() {
            try {
                new BotTelemetryClientImpl(null);
            } catch (Exception e) {
                Assert.assertEquals("IllegalArgumentException", e.getCause());
            }
        }

        public void NonNullTelemetryClientSucceeds() {
            TelemetryClient telemetryClient = new TelemetryClient();

            BotTelemetryClient botTelemetryClient = new BotTelemetryClientImpl(telemetryClient);
        }

        public void OverrideTest() {
            TelemetryClient telemetryClient = new TelemetryClient();
            MyBotTelemetryClient botTelemetryClient = new MyBotTelemetryClient(telemetryClient);
        }
    }

    public class TrackTelemetryTests {
        private BotTelemetryClient botTelemetryClient;
        private TelemetryChannel mockTelemetryChannel;

        public TrackTelemetryTests()
        {
            mockTelemetryChannel = Mockito.mock(TelemetryChannel.class);

            TelemetryConfiguration telemetryConfiguration = new TelemetryConfiguration();
            telemetryConfiguration.setInstrumentationKey("UNITTEST-INSTRUMENTATION-KEY");
            telemetryConfiguration.setChannel(mockTelemetryChannel);
            TelemetryClient telemetryClient = new TelemetryClient(telemetryConfiguration);

            botTelemetryClient = new BotTelemetryClientImpl(telemetryClient);
        }

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

            verify(mockTelemetryChannel.send(isA(AvailabilityTelemetry (t -> t.getName() == "test"))));
            verify(mockTelemetryChannel.send(isA(AvailabilityTelemetry (t -> t.getMessage()) == "message")));
            verify(mockTelemetryChannel.send(isA(AvailabilityTelemetry (t -> t.Properties["hello"] == "value"))));
            verify(mockTelemetryChannel.send(isA(AvailabilityTelemetry (t -> t.getMetrics["metric"] == 0.6))));
        }

        public void TrackEventTest()
        {
            botTelemetryClient.trackEvent("test", new HashMap<String, String>() {{ put("hello", "value"); }}, new HashMap<String, Double>() {{ put("metric", 0.6); }});

            verify(mockTelemetryChannel.send(isA(EventTelemetry(t -> t.getName() == "test"))));
            verify(mockTelemetryChannel.send(isA(EventTelemetry(t -> t.Properties["hello"] == "value"))));
            verify(mockTelemetryChannel.send(isA(EventTelemetry(t -> t.getMetrics["metric"] == 0.6))));
        }

        public void TrackDependencyTest()
        {
            botTelemetryClient.trackDependency("test", "target", "dependencyname", "data", OffsetDateTime.now(), Duration.ofSeconds(1000), "result", false);

            verify(mockTelemetryChannel.send(isA(RemoteDependencyTelemetry(t -> t.getVer() == "test"))));
            verify(mockTelemetryChannel.send(isA(RemoteDependencyTelemetry(t -> t.Target == "target"))));
            verify(mockTelemetryChannel.send(isA(RemoteDependencyTelemetry(t -> t.getName() == "dependencyname"))));
            verify(mockTelemetryChannel.send(isA(RemoteDependencyTelemetry(t -> t.getData() == "data"))));
            verify(mockTelemetryChannel.send(isA(RemoteDependencyTelemetry(t -> t.ResultCode == "result"))));
            verify(mockTelemetryChannel.send(isA(RemoteDependencyTelemetry(t -> t.isSuccess() == false))));
        }

        public void TrackExceptionTest()
        {
            Exception expectedException = new Exception("test-exception");

            botTelemetryClient.trackException(expectedException, new HashMap<String, String>() {{ put("foo", "bar"); }}, new HashMap<String, Double>() {{ put("metric", 0.6); }});
            verify(mockTelemetryChannel.send(isA(ExceptionTelemetry(t -> t.Exception == expectedException))));
            verify(mockTelemetryChannel.send(isA(ExceptionTelemetry(t -> t.Properties["foo"] == "bar"))));
            verify(mockTelemetryChannel.send(isA(ExceptionTelemetry(t -> t.getMetrics["metric"] == 0.6))));
        }

        public void TrackTraceTest()
        {
            botTelemetryClient.trackTrace("hello", Severity.CRITICAL, new HashMap<String, String>() {{ put("foo", "bar"); }});

            verify(mockTelemetryChannel.send(isA(TraceTelemetry(t -> t.getMessage() == "hello"))));
            verify(mockTelemetryChannel.send(isA(TraceTelemetry(t -> t.getSeverityLevel() == Severity.CRITICAL))));
            verify(mockTelemetryChannel.send(isA(TraceTelemetry(t -> t.Properties["foo"] == "bar"))));
        }

        public void TrackPageViewTest()
        {
            botTelemetryClient.trackDialogView("test", new HashMap<String, String>() {{ put("hello", "value"); }}, new HashMap<String, Double>() {{ put("metric", 0.6); }});

            verify(mockTelemetryChannel.send(isA(PageViewTelemetry(t -> t.getName() == "test"))));
            verify(mockTelemetryChannel.send(isA(PageViewTelemetry(t -> t.Properties["hello"] == "value"))));
            verify(mockTelemetryChannel.send(isA(PageViewTelemetry(t -> t.getMetrics["metric"] == 0.6))));
        }
    }
}
