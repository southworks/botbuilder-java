// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.bot.builder.BotTelemetryClient;
import org.junit.Assert;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;

public class BotTelemetryCientTests {
    public class ConstructorTests {
        public void NullTelemetryClientThrows() {
            try {
                new BotTelemetryClient(null);
            } catch (Exception e) {
                Assert.assertEquals("IllegalArgumentException", e.getCause());
            }
        }

        public void NonNullTelemetryClientSucceeds() {
            TelemetryClient telemetryClient = new TelemetryClient();

            BotTelemetryClient botTelemetryClient = new BotTelemetryClient(telemetryClient);
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

            TelemetryConfiguration telemetryConfiguration = new TelemetryConfiguration("UNITTEST-INSTRUMENTATION-KEY", mockTelemetryChannel.Object);
            TelemetryClient telemetryClient = new TelemetryClient(telemetryConfiguration);

            botTelemetryClient = new BotTelemetryClient(telemetryClient);
        }

        public void TrackAvailabilityTest()
        {
            botTelemetryClient.trackAvailability(
                "test",
                DateTimeOffset.Now,
                new TimeSpan(1000),
                "run location",
                true,
                "message",
                new Dictionary<string, string>() { { "hello", "value" } },
                new Dictionary<string, double>() { { "metric", 0.6 } });

            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<AvailabilityTelemetry>(t => t.Name == "test")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<AvailabilityTelemetry>(t => t.Message == "message")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<AvailabilityTelemetry>(t => t.Properties["hello"] == "value")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<AvailabilityTelemetry>(t => t.Metrics["metric"] == 0.6)));
        }

        public void TrackEventTest()
        {
            botTelemetryClient.trackEvent("test", new Dictionary<string, string>() { { "hello", "value" } }, new Dictionary<string, double>() { { "metric", 0.6 } });

            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<EventTelemetry>(t => t.Name == "test")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<EventTelemetry>(t => t.Properties["hello"] == "value")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<EventTelemetry>(t => t.Metrics["metric"] == 0.6)));
        }

        public void TrackDependencyTest()
        {
            botTelemetryClient.trackDependency("test", "target", "dependencyname", "data", DateTimeOffset.Now, new TimeSpan(10000), "result", false);

            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<DependencyTelemetry>(t => t.Type == "test")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<DependencyTelemetry>(t => t.Target == "target")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<DependencyTelemetry>(t => t.Name == "dependencyname")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<DependencyTelemetry>(t => t.Data == "data")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<DependencyTelemetry>(t => t.ResultCode == "result")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<DependencyTelemetry>(t => t.Success == false)));
        }

        public void TrackExceptionTest()
        {
            Exception expectedException = new Exception("test-exception");

            botTelemetryClient.trackException(expectedException, new Dictionary<string, string>() { { "foo", "bar" } }, new Dictionary<string, double>() { { "metric", 0.6 } });
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<ExceptionTelemetry>(t => t.Exception == expectedException)));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<ExceptionTelemetry>(t => t.Properties["foo"] == "bar")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<ExceptionTelemetry>(t => t.Metrics["metric"] == 0.6)));
        }

        public void TrackTraceTest()
        {
            botTelemetryClient.trackTrace("hello", Severity.Critical, new Dictionary<string, string>() { { "foo", "bar" } });

            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<TraceTelemetry>(t => t.Message == "hello")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<TraceTelemetry>(t => t.SeverityLevel == SeverityLevel.Critical)));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<TraceTelemetry>(t => t.Properties["foo"] == "bar")));
        }

        public void TrackPageViewTest()
        {
            botTelemetryClient.trackDialogView("test", new Dictionary<string, string>() { { "hello", "value" } }, new Dictionary<string, double>() { { "metric", 0.6 } });

            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<PageViewTelemetry>(t => t.Name == "test")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<PageViewTelemetry>(t => t.Properties["hello"] == "value")));
            verify(mockTelemetryChannel => mockTelemetryChannel.send(It.Is<PageViewTelemetry>(t => t.Metrics["metric"] == 0.6)));
        }
    }
}
