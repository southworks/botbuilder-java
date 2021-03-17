// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.schema;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class NanoClockHelper extends Clock
{
    private final Clock clock;

    private final long initialNanos;

    private final Instant initialInstant;

    public NanoClockHelper()
    {
        this(Clock.systemUTC());
    }

    public NanoClockHelper(final Clock clock)
    {
        this.clock = clock;
        initialInstant = clock.instant();
        initialNanos = getSystemNanos();
    }

    @Override
    public ZoneId getZone()
    {
        return clock.getZone();
    }

    @Override
    public Instant instant()
    {
        return initialInstant.plusNanos(getSystemNanos() - initialNanos);
    }

    @Override
    public Clock withZone(final ZoneId zone)
    {
        return new NanoClockHelper(clock.withZone(zone));
    }

    private long getSystemNanos()
    {
        return System.nanoTime();
    }
}

