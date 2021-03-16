// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.applicationinsights.internal.schemav2.AvailabilityData;
import com.microsoft.applicationinsights.internal.util.LocalStringsUtils;
import com.microsoft.applicationinsights.internal.util.Sanitizer;
import com.microsoft.applicationinsights.telemetry.BaseSampleSourceTelemetry;
import com.microsoft.applicationinsights.telemetry.Duration;
import java.util.Date;
import java.util.concurrent.ConcurrentMap;

/**
 * We took this class from https://github.com/microsoft/ApplicationInsights-Java/issues/1099
 * as this is not already migrated in ApplicationInsights-Java library
 */
public final class AvailabilityTelemetry extends BaseSampleSourceTelemetry<AvailabilityData> {
    private Double samplingPercentage;
    private final AvailabilityData data;

    public static final String ENVELOPE_NAME = "Availability";

    public static final String BASE_TYPE = "AvailabilityData";

    public AvailabilityTelemetry() {
        this.data = new AvailabilityData();
        initialize(this.data.getProperties());
        setId(LocalStringsUtils.generateRandomIntegerId());

        // Setting mandatory fields.
        setTimestamp(new Date());
        setSuccess(true);
    }

    public AvailabilityTelemetry(String name, Duration duration, String runLocation, String message,
                                 boolean success, ConcurrentMap<String, Double> measurements,
                                 ConcurrentMap<String, String> properties) {

        this.data = new AvailabilityData();

        this.data.setProperties(properties);
        this.data.setMeasurements(measurements);

        initialize(this.data.getProperties());

        setId(LocalStringsUtils.generateRandomIntegerId());

        setTimestamp(new Date());

        setName(name);
        setRunLocation(runLocation);
        setDuration(duration);
        setSuccess(success);
    }

    @Override
    public int getVer() {
        return getData().getVer();
    }

    public ConcurrentMap<String, Double> getMetrics() {
        return data.getMeasurements();
    }

    @Override
    public void setTimestamp(Date timestamp) {
        if (timestamp == null) {
            timestamp = new Date();
        }

        super.setTimestamp(timestamp);
    }

    public String getName() {
        return data.getName();
    }

    public void setName(String name) {
        data.setName(name);
    }

    public String getRunLocation() {
        return data.getRunLocation();
    }

    public void setRunLocation(String runLocation) {
        data.setRunLocation(runLocation);
    }

    public String getId() {
        return data.getId();
    }

    public void setId(String id) {
        data.setId(id);
    }

    public boolean isSuccess() {
        return data.getSuccess();
    }

    public void setSuccess(boolean success) {
        data.setSuccess(success);
    }

    public Duration getDuration() {
        return data.getDuration();
    }

    public void setDuration(Duration duration) {
        data.setDuration(duration);
    }

    @Override
    public Double getSamplingPercentage() {
        return samplingPercentage;
    }

    @Override
    public void setSamplingPercentage(Double samplingPercentage) {
        this.samplingPercentage = samplingPercentage;
    }

    @Override
    @Deprecated
    protected void additionalSanitize() {
        data.setName(Sanitizer.sanitizeName(data.getName()));
        data.setId(Sanitizer.sanitizeName(data.getId()));
        Sanitizer.sanitizeMeasurements(getMetrics());
    }

    @Override
    protected AvailabilityData getData() {
        return data;
    }

    @Override
    public String getEnvelopName() {
        return ENVELOPE_NAME;
    }

    @Override
    public String getBaseTypeName() {
        return BASE_TYPE;
    }
}

