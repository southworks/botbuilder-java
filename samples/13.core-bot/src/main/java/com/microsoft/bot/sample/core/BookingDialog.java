// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.core;

public class BookingDialog extends CancelAndHelpDialog {
    private final String destinationStepMsgText = "Where would you like to travel to?";
    private final String originStepMsgText = "Where are you traveling from?";

    public BookingDialog() {
        super(BookingDialog.class.getName());
    }
}
