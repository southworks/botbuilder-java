// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.applicationinsights;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.*;
import com.microsoft.bot.builder.BotTelemetryClient;
import com.microsoft.bot.builder.Severity;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class BotTelemetryClientImpl implements BotTelemetryClient {
    private final TelemetryClient telemetryClient;
    private String dependencyTypeName;
    private String target;
    private String dependencyName;
    private String commandName;
    private String data;
    private Date startTime;
    private Duration duration;
    private String resultCode;
    private Boolean success;

    /** <summary>
    * Initializes a new instance of the <see cref="BotTelemetryClient"/> class.
    * </summary>
    * <param name="telemetryClient">The telemetry client to forward bot events to.</param>
    */
    public BotTelemetryClientImpl(TelemetryClient telemetryClient)
    {
        this.telemetryClient = telemetryClient;
        telemetryClient = telemetryClient;
        if(telemetryClient == null){
            throw new IllegalArgumentException(telemetryClient.getClass().getName());
        }
    }

    /**
     * Send information about availability of an application.
     * @param name Availability test name
     * @param timeStamp The time when the availability was captured.
     * @param duration The time taken for the availability test to run.
     * @param runLocation Name of the location the availability test was run from.
     * @param success True if the availability test ran successfully.
     * @param message Error message on availability test run failure.
     * @param properties Named string values you can use to classify and search for this availability telemetry.
     * @param measurements Additional values associated with this availability telemetry.
     */

    public void trackAvailability(String name, OffsetDateTime timeStamp, Duration duration, String runLocation, Boolean success, String message, @Nullable ConcurrentMap<String, String> properties, @Nullable ConcurrentMap<String, Double> measurements)
    {
        AvailabilityTelemetry telemetry = new AvailabilityTelemetry(name, duration, runLocation, message, success, measurements, properties);
        if (properties != null)
        {
            for(Map.Entry<String, String> p : properties.entrySet())
            {
                telemetry.getProperties().put(p.getKey(), p.getValue());
            }
        }

        if (measurements != null)
        {
            for(Map.Entry<String, Double> p : measurements.entrySet())
            {
                telemetry.getMetrics().put(p.getKey(), p.getValue());
            }
        }

        telemetryClient.track(telemetry);
    }

    /**
     * Send information about an external dependency (outgoing call) in the application.
     * @param dependencyTypeName Name of the command initiated with this dependency call. Low cardinality value.
     * Examples are SQL, Azure table, and HTTP.
     * @param target External dependency target.
     * @param duration The time taken for the availability test to run.
     * @param dependencyName Name of the command initiated with this dependency call. Low cardinality value.
     * Examples are stored procedure name and URL path template.
     * @param data Command initiated by this dependency call. Examples are SQL statement and HTTP
     * URL's with all query parameters.
     * @param startTime The time when the dependency was called.
     * @param duration The time taken by the external dependency to handle the call.
     * @param resultCode Result code of dependency call execution.
     * @param success True if the dependency call was handled successfully.
     */

    public void trackDependency(String dependencyTypeName, String target, String dependencyName, String commandName, String data, Date startTime, Duration duration, String resultCode, Boolean success)
    {
        this.dependencyTypeName = dependencyTypeName;
        this.target = target;
        this.dependencyName = dependencyName;
        this.commandName = commandName;
        this.data = data;
        this.startTime = startTime;
        this.duration = duration;
        this.resultCode = resultCode;
        this.success = success;

        RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry(dependencyName, commandName, duration, success);

        telemetryClient.trackDependency(telemetry);
    }

    @Override
    public void trackAvailability(String name, OffsetDateTime timeStamp, java.time.Duration duration, String runLocation, boolean success, String message, Map<String, String> properties, Map<String, Double> metrics) {

    }

    @Override
    public void trackDependency(String dependencyTypeName, String target, String dependencyName, String data, OffsetDateTime startTime, java.time.Duration duration, String resultCode, boolean success) {

    }

    /**
     * Logs custom events with extensible named fields.
     * @param eventName A name for the event.
     * @param properties Named string values you can use to search and classify events.
     * @param measurements Measurements associated with this event.
     */

    public void trackEvent(String eventName, Map<String, String> properties, Map<String, Double> measurements)
    {
        EventTelemetry telemetry = new EventTelemetry(eventName);
        if (properties != null)
        {
            for(Map.Entry<String, String> p : properties.entrySet())
            {
                telemetry.getProperties().put(p.getKey(), p.getValue());
            }
        }

        if (measurements != null)
        {
            for(Map.Entry<String, Double> p : measurements.entrySet())
            {
                telemetry.getMetrics().put(p.getKey(), p.getValue());
            }
        }

        telemetryClient.trackEvent(telemetry);
    }

    /**
     * Logs a system exception.
     * @param exception The exception to log.
     * @param properties Named string values you can use to search and classify events.
     * @param measurements Measurements associated with this event.
     */

    public void trackException(Exception exception, Map<String, String> properties, Map<String, Double> measurements)
    {
        ExceptionTelemetry telemetry = new ExceptionTelemetry(exception);
        if (properties != null)
        {
            for(Map.Entry<String, String> p : properties.entrySet())
            {
                telemetry.getProperties().put(p.getKey(), p.getValue());
            }
        }

        if (measurements != null)
        {
            for(Map.Entry<String, Double> p : measurements.entrySet())
            {
                telemetry.getMetrics().put(p.getKey(), p.getValue());
            }
        }

        telemetryClient.trackException(telemetry);
    }

    /**
     * Send a trace message.
     * @param message Message to display.
     * @param severityLevel Trace severity level <see cref="Severity"/>.
     * @param properties Named string values you can use to search and classify events.
     */

    public void trackTrace(String message, Severity severityLevel, Map<String, String> properties)
    {
        TraceTelemetry telemetry = new TraceTelemetry(message);

        if (properties != null)
        {
            for(Map.Entry<String, String> p : properties.entrySet())
            {
                telemetry.getProperties().put(p.getKey(), p.getValue());
            }
        }

        telemetryClient.trackTrace(telemetry);
    }

    @Override
    public void trackDialogView(String dialogName, Map<String, String> properties, Map<String, Double> metrics) {

    }

    /**
     * Logs a dialog entry / as an Application Insights page view.
     * @param dialogName The name of the dialog to log the entry / start for.
     * @param properties Named string values you can use to search and classify events.
     * @param measurements Measurements associated with this event.
     */

    public void trackPageView(String dialogName, Map<String, String> properties, Map<String, Double> measurements)
    {
        PageViewTelemetry telemetry = new PageViewTelemetry(dialogName);

        if (properties != null)
        {
            for(Map.Entry<String, String> p : properties.entrySet())
            {
                telemetry.getProperties().put(p.getKey(), p.getValue());
            }
        }

        if (measurements != null)
        {
            for(Map.Entry<String, Double> p : measurements.entrySet())
            {
                telemetry.getMetrics().put(p.getKey(), p.getValue());
            }
        }

        telemetryClient.trackPageView(telemetry);
    }

    /**
     * Flushes the in-memory buffer and any metrics being pre-aggregated.
     */

    public void flush() {
        telemetryClient.flush();
    }
}
