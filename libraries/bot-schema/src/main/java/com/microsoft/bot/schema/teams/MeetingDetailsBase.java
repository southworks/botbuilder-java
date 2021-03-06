// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.schema.teams;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Specific details of a Teams meeting.
 */
public class MeetingDetailsBase {
    @JsonProperty(value = "id")
    private String id;

    @JsonProperty(value = "joinUrl")
    private String joinUrl;

    @JsonProperty(value = "title")
    private String title;

    /**
     * Initializes a new instance.
     */
    public MeetingDetailsBase() {
    }

    /**
     * Gets the meeting's Id, encoded as a BASE64 String.
     * 
     * @return The meeting's Id, encoded as a BASE64 String.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the meeting's Id, encoded as a BASE64 String.
     * 
     * @param withId The meeting's Id, encoded as a BASE64 String.
     */
    public void setId(String withId) {
        id = withId;
    }

    /**
     * Gets the URL used to join the meeting.
     * 
     * @return The URL used to join the meeting.
     */
    public String getJoinUrl() {
        return joinUrl;
    }

    /**
     * Sets the URL used to join the meeting.
     * 
     * @param withJoinUrl The URL used to join the meeting.
     */
    public void setJoinUrl(String withJoinUrl) {
        joinUrl = withJoinUrl;
    }

    /**
     * Gets the title of the meeting.
     * 
     * @return The title of the meeting.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title of the meeting.
     * 
     * @param withTitle The title of the meeting.
     */
    public void setTitle(String withTitle) {
        title = withTitle;
    }
}
