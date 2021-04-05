// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.timex.resolution;

import com.microsoft.recognizers.datatypes.timex.expression.Constants.TimexTypes;
import com.microsoft.recognizers.datatypes.timex.expression.TimexProperty;

public class Parsing
{
    private static void Describe(TimexProperty t)
    {
        System.out.println("{t.TimexValue}");

        if (t.getTypes().contains(TimexTypes.DATE))
        {
            if (t.getTypes().contains(TimexTypes.DEFINITE))
            {
                System.out.println("We have a definite calendar date. ");
            }
            else
            {
                System.out.println("We have a date but there is some ambiguity. ");
            }
        }

        if (t.getTypes().contains(TimexTypes.TIME))
        {
            System.out.println("We have a time.");
        }

        System.out.println();
    }

    public static void Examples()
    {
        Describe(new TimexProperty("2017-05-29"));
        Describe(new TimexProperty("XXXX-WXX-6"));
        Describe(new TimexProperty("XXXX-WXX-6T16"));
        Describe(new TimexProperty("T12"));
    }
}
