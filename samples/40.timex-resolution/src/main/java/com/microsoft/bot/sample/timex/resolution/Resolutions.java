// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.timex.resolution;

import com.microsoft.recognizers.datatypes.timex.expression.Resolution;
import com.microsoft.recognizers.datatypes.timex.expression.TimexResolver;
import java.time.LocalDateTime;

/**
 * Given the TIMEX expressions it is easy to create the computed example values that the recognizer gives.
 */
public class Resolutions {
    public static void examples() {
        // When you give the recognizer the text "Wednesday 4 o'clock" you get these distinct TIMEX values back.
        
        LocalDateTime today = LocalDateTime.now();
        Resolution resolution = TimexResolver.resolve(new String[] { "XXXX-WXX-3T04", "XXXX-WXX-3T16" }, today);
        
        System.out.println(resolution.getValues().size());
        
        System.out.println(resolution.getValues().get(0).getTimex());
        System.out.println(resolution.getValues().get(0).getType());
        System.out.println(resolution.getValues().get(0).getValue());
        
        System.out.println(resolution.getValues().get(1).getTimex());
        System.out.println(resolution.getValues().get(1).getType());
        System.out.println(resolution.getValues().get(1).getValue());
        
        System.out.println(resolution.getValues().get(2).getTimex());
        System.out.println(resolution.getValues().get(2).getType());
        System.out.println(resolution.getValues().get(2).getValue());
    
        System.out.println(resolution.getValues().get(3).getTimex());
        System.out.println(resolution.getValues().get(3).getType());
        System.out.println(resolution.getValues().get(3).getValue());        
    }
}
