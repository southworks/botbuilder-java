// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.corebot.app.insights;

public class BookingDetails {
    private String destination;
    private String origin;
    private String travelDate;
    
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String withDestination) {
        destination = withDestination;
    }
    
    public String getOrigin() {
        return origin;
    }
    
    public void setOrigin(String withOrigin) {
        origin = withOrigin;
    }
    
    public String getTravelDate() {
        return travelDate;
    }
    
    public void setTravelDate(String withTravelDate) {
        travelDate = withTravelDate;
    }
}
