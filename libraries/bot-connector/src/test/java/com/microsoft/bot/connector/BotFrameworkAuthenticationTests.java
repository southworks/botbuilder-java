// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MT License.

package com.microsoft.bot.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.connector.authentication.AuthenticationConfiguration;
import com.microsoft.bot.connector.authentication.AuthenticationConstants;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthentication;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthenticationFactory;
import com.microsoft.bot.connector.authentication.MicrosoftAppCredentials;
import com.microsoft.bot.connector.authentication.ServiceClientCredentialsFactory;
import com.microsoft.bot.connector.skills.BotFrameworkClient;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ConversationAccount;
import com.microsoft.bot.schema.TypedInvokeResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

public class BotFrameworkAuthenticationTests {

    @Test
    public void createsBotFrameworkClient() throws URISyntaxException, IOException {
        // Arrange
        String fromBotId = "from-bot-id";
        String toBotId = "to-bot-id";
        String loginUrl = AuthenticationConstants.TO_CHANNEL_FROM_BOT_LOGIN_URL_TEMPLATE;
        URI toUrl = new URI("http://test1.com/test");

        ServiceClientCredentialsFactory credentialFactoryMock = Mockito.mock(ServiceClientCredentialsFactory.class);
        Mockito.when(
            credentialFactoryMock.createCredentials(
                fromBotId,
                toBotId,
                loginUrl,
                Mockito.anyBoolean())
        ).thenReturn(CompletableFuture.completedFuture(MicrosoftAppCredentials.empty()));

        OkHttpClient httpClientMock = Mockito.mock(OkHttpClient.class);
        Mockito.when(
            httpClientMock.newCall(Mockito.any(Request.class)).execute()
        ).thenReturn(new Response.Builder()
            .code(200)
            .body(ResponseBody.create(
                MediaType.get("application/json; charset=utf-8"),
                "{\"hello\": \"world\"}"))
            .build()
        );

        BotFrameworkAuthentication bfa = BotFrameworkAuthenticationFactory.create(
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            credentialFactoryMock,
            new AuthenticationConfiguration(),
            httpClientMock
        );

        URI serviceUrl = new URI("http://root-bot/service-url");
        String conversationId = "conversation-id";
        ConversationAccount conversationAccount = new ConversationAccount();
        conversationAccount.setId("conversationiid");
        conversationAccount.setName("conversation-name");
        Activity activity = new Activity(ActivityTypes.MESSAGE);
        activity.setChannelId("channel-id");
        activity.setServiceUrl("service-url");
        activity.setLocale("locale");
        activity.setConversation(conversationAccount);

        // Act
        BotFrameworkClient bfc = bfa.createBotFrameworkClient();
        TypedInvokeResponse invokeResponse = bfc.postActivity(fromBotId, toBotId, toUrl, serviceUrl, conversationId, activity, TypedInvokeResponse.class).join();

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode testData = mapper.readTree(mapper.writeValueAsString(invokeResponse));

        // Assert
        Assert.assertEquals("world", testData.get("hello").toString());
    }
}
