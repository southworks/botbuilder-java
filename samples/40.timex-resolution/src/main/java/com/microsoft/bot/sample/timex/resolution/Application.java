// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.timex.resolution;

public class Application {
    static void main(String[] args) {
        // Creating TIMEX expressions from natural language using the Recognizer package.
        Ambiguity.dateAmbiguity();
        Ambiguity.timeAmbiguity();
        Ambiguity.dateTimeAmbiguity();
        Ranges.dateRange();
        Ranges.timeRange();
        
        // Manipulating TIMEX expressions in code using the TIMEX Datatype package.
        Parsing.examples();
        LanguageGeneration.examples();
        Resolutions.examples();
        Constraints.examples();
    }
}