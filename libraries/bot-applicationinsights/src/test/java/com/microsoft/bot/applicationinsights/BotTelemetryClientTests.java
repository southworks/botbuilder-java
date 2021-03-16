// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.bot.builder.BotTelemetryClient;
import org.junit.Assert;

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

    }
}
