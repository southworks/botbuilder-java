// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.multilingual;

import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.builder.InvokeResponse;
import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.schema.Activity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.CompletableFuture;

/**
 * This controller is created to handle a request.
 *
 * @see MultiLingualBot
 * @see Application
 */
@RestController
public class BotController  {

    /**
     * The BotFrameworkHttpAdapter to use. Note is is provided by dependency
     * injection via the constructor.
     *
     * @see com.microsoft.bot.integration.spring.BotDependencyConfiguration
     */
    private final BotFrameworkHttpAdapter adapter;
    /**
     * The Bot to use. Note is is provided by dependency
     * injection via the constructor.
     *
     * @see com.microsoft.bot.integration.spring.BotDependencyConfiguration
     */
    private final Bot bot;

    @Autowired
    public BotController(BotFrameworkHttpAdapter withAdapter, Bot withBot) {
        this.adapter = withAdapter;
        this.bot = withBot;
    }

    @PostMapping("/api/messages")
    public CompletableFuture<InvokeResponse> post(
        @RequestBody Activity activity,
        @RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
            return adapter.processActivity(authHeader, activity, turnContext -> bot.onTurn(turnContext));
    }
}
