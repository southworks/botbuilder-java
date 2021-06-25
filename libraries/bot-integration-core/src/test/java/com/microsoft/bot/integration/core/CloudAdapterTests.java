// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.integration.core;

import com.auth0.jwt.interfaces.Claim;
import com.microsoft.aad.msal4j.HttpMethod;
import com.microsoft.aad.msal4j.HttpRequest;
import com.microsoft.aad.msal4j.HttpResponse;
import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.builder.BotCallbackHandler;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.connector.ConnectorClient;
import com.microsoft.bot.connector.authentication.AuthenticateRequestResult;
import com.microsoft.bot.connector.authentication.AuthenticationConfiguration;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthentication;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthenticationFactory;
import com.microsoft.bot.connector.authentication.ClaimsIdentity;
import com.microsoft.bot.connector.authentication.ConnectorFactory;
import com.microsoft.bot.connector.authentication.GovernmentAuthenticationConstants;
import com.microsoft.bot.connector.authentication.PasswordServiceClientCredentialFactory;
import com.microsoft.bot.connector.authentication.UserTokenClient;
import com.microsoft.bot.integration.CloudAdapter;
import com.microsoft.bot.restclient.credentials.ServiceClientCredentials;
import com.microsoft.bot.restclient.serializer.JacksonAdapter;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ConversationAccount;
import com.microsoft.bot.schema.ConversationReference;
import com.microsoft.bot.schema.InvokeResponse;
import com.microsoft.bot.schema.SignInResource;
import com.microsoft.bot.schema.TokenExchangeRequest;
import com.microsoft.bot.schema.TokenExchangeState;
import com.microsoft.bot.schema.TokenResponse;
import com.microsoft.bot.schema.TokenStatus;
import com.sun.jndi.toolkit.url.Uri;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.omg.IOP.Encoding;

import java.io.IOException;
import java.security.Identity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

public class CloudAdapterTests {

    @Test
    public void basicMessageActivity() {
        // Arrange
        Bot botMock = mock(Bot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture());

        // Act
        CloudAdapter adapter = new CloudAdapter();
        adapter.processIncomingActivity("", createMessageActivity(), botMock);

        verify(botMock, atMostOnce()).onTurn(any (TurnContext.class));
    }

    @Test
    public void invokeActivity() {
        // Arrange
        InvokeResponseBot botMock = mock(InvokeResponseBot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture());

        // Act
        CloudAdapter adapter = new CloudAdapter();
        adapter.processIncomingActivity("", createInvokeActivity(), botMock);

        verify(botMock, atMostOnce()).onTurn(any (TurnContext.class));
    }

    @Test
    public void messageActivityWithHttpClient() {
        // Arrange
        Bot bot = new MessageBot();

        // Act
        BotFrameworkAuthentication cloudEnvironment = BotFrameworkAuthenticationFactory.create(
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new PasswordServiceClientCredentialFactory(),
            new AuthenticationConfiguration());
        CloudAdapter adapter = new CloudAdapter(cloudEnvironment);
        adapter.processIncomingActivity("", createMessageActivity(), bot);

        // Assert
        verify()
    }

    @Ignore
    @Test
    public void badRequest() {
        // Arrange
        /*var headerDictionaryMock = new Mock<IHeaderDictionary>();
        headerDictionaryMock.Setup(h => h[It.Is<string>(v => v == "Authorization")]).Returns<string>(null);*/

        /*HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(createBadRequestStream());
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock);*/

        HttpResponse httpResponseMock = mock(HttpResponse.class);
        httpResponseMock.SetupProperty(x => x.StatusCode);

        Bot botMock = mock(Bot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture());

        // Act
        CloudAdapter adapter = new CloudAdapter();
        adapter.processIncomingActivity("", createMessageActivity(), botMock);

        // Assert
        verify(botMock, never()).onTurn(any (TurnContext.class));
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, httpResponseMock.statusCode());
    }

    @Test
    public void injectCloudEnvironment() {
        // Arrange
        Bot botMock = mock(Bot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture(null));

        AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
        authenticateRequestResult.setClaimsIdentity(new ClaimsIdentity(""));
        authenticateRequestResult.setConnectorFactory(new TestConnectorFactory());
        authenticateRequestResult.setAudience("audience");
        authenticateRequestResult.setCallerId("callerId");

        TestUserTokenClient userTokenClient = new TestUserTokenClient("appId");

        BotFrameworkAuthentication cloudEnvironmentMock = mock(BotFrameworkAuthentication.class);
        when(cloudEnvironmentMock.authenticateRequest(any (Activity.class), any (String.class))).thenReturn(CompletableFuture.completedFuture(authenticateRequestResult));
        when(cloudEnvironmentMock.createUserTokenClient(any (ClaimsIdentity.class))).thenReturn(CompletableFuture.completedFuture(userTokenClient));

        // Act
        CloudAdapter adapter = new CloudAdapter(cloudEnvironmentMock);
        adapter.processIncomingActivity("", createMessageActivity(), botMock);

        // Assert
        verify(botMock, atMostOnce()).onTurn(any (TurnContext.class));
        verify(cloudEnvironmentMock, atMostOnce()).authenticateRequest(any (Activity.class), any(String.class));
    }

    @Test
    public void cloudAdapterProvidesUserTokenClient() {
        // this is just a basic test to verify the wire-up of a UserTokenClient in the CloudAdapter
        // there is also some coverage for the internal code that creates the TokenExchangeState string

        // Arrange
        String appId = "appId";
        String userId = "userId";
        String channelId = "channelId";
        String conversationId = "conversationId";
        String recipientId = "botId";
        String relatesToActivityId = "relatesToActivityId";
        String connectionName = "connectionName";

        AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
        authenticateRequestResult.setClaimsIdentity(new ClaimsIdentity(""));
        authenticateRequestResult.setConnectorFactory(new TestConnectorFactory());
        authenticateRequestResult.setAudience("audience");
        authenticateRequestResult.setCallerId("callerId");

        TestUserTokenClient userTokenClient = new TestUserTokenClient(appId);

        BotFrameworkAuthentication cloudEnvironmentMock = mock(BotFrameworkAuthentication.class);
        when(cloudEnvironmentMock.authenticateRequest(any (Activity.class), any (String.class))).thenReturn(CompletableFuture.completedFuture(authenticateRequestResult));
        when(cloudEnvironmentMock.createUserTokenClient(any (ClaimsIdentity.class))).thenReturn(CompletableFuture.completedFuture(userTokenClient));

        UserTokenClientBot bot = new UserTokenClientBot(connectionName);

        // Act
        Activity activity = createMessageActivity(userId, channelId, conversationId, recipientId, relatesToActivityId);
        CloudAdapter adapter = new CloudAdapter(cloudEnvironmentMock);
        adapter.processIncomingActivity("", activity, bot);

        // Assert
        Object[] args_ExchangeToken = userTokenClient.getRecord("exchangeToken");
        Assert.assertEquals(userId, (String) args_ExchangeToken[0]);
        Assert.assertEquals(connectionName, (String)args_ExchangeToken[1]);
        Assert.assertEquals(channelId, (String) args_ExchangeToken[2]);
        Assert.assertEquals("TokenExchangeRequest", args_ExchangeToken[3].getClass().getName());

        Object[] args_GetAadTokens = userTokenClient.getRecord("getSignInResource");
        Assert.assertEquals(userId, (String) args_GetAadTokens[0]);
        Assert.assertEquals(connectionName, (String) args_GetAadTokens[1]);
        Assert.assertEquals("x", ((String[]) args_GetAadTokens[2])[0]);
        Assert.assertEquals("y", ((String[]) args_GetAadTokens[2])[1]);

        Assert.assertEquals(channelId, (String) args_GetAadTokens[3]);

        Object[] args_GetSignInResource = userTokenClient.getRecord("getSignInResource");

        // this code is testing the internal CreateTokenExchangeState function by doing the work in reverse
        String state = (String) args_GetSignInResource[0];
        String json = "";
        TokenExchangeState tokenExchangeState = null;

        try {
            JacksonAdapter jacksonAdapter = new JacksonAdapter();
            json = jacksonAdapter.serialize(state);
            tokenExchangeState = jacksonAdapter.deserialize(json, TokenExchangeState.class);
        } catch (IOException e) {
        }

        Assert.assertEquals(connectionName, tokenExchangeState.getConnectionName());
        Assert.assertEquals(appId, tokenExchangeState.getMsAppId());
        Assert.assertEquals(conversationId, tokenExchangeState.getConversation().getConversation().getId());
        Assert.assertEquals(recipientId, tokenExchangeState.getConversation().getBot().getId());
        Assert.assertEquals(relatesToActivityId, tokenExchangeState.getRelatesTo().getActivityId());

        Assert.assertEquals("finalRedirect", (String) args_GetSignInResource[1]);

        Object[] args_GetTokenStatus = userTokenClient.getRecord("GetTokenStatusAsync");
        Assert.assertEquals(userId, (String) args_GetTokenStatus[0]);
        Assert.assertEquals(channelId, (String) args_GetTokenStatus[1]);
        Assert.assertEquals("includeFilter", (String)args_GetTokenStatus[2]);

        Object[] args_GetUserToken = userTokenClient.getRecord("GetUserTokenAsync");
        Assert.assertEquals(userId, (String)args_GetUserToken[0]);
        Assert.assertEquals(connectionName, (String)args_GetUserToken[1]);
        Assert.assertEquals(channelId, (String)args_GetUserToken[2]);
        Assert.assertEquals("magicCode", (String)args_GetUserToken[3]);

        Object[] args_SignOutUser = userTokenClient.getRecord("SignOutUserAsync");
        Assert.assertEquals(userId, (String) args_SignOutUser[0]);
        Assert.assertEquals(connectionName, (String) args_SignOutUser[1]);
        Assert.assertEquals(channelId, (String) args_SignOutUser[2]);
    }

    @Test
    public void cloudAdapterConnectorFactory() {
        // this is just a basic test to verify the wire-up of a ConnectorFactory in the CloudAdapter

        // Arrange
        ClaimsIdentity claimsIdentity = new ClaimsIdentity("");

        AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
        authenticateRequestResult.setClaimsIdentity(claimsIdentity);
        authenticateRequestResult.setConnectorFactory(new TestConnectorFactory());
        authenticateRequestResult.setAudience("audience");
        authenticateRequestResult.setCallerId("callerId");

        TestUserTokenClient userTokenClient = new TestUserTokenClient("appId");

        BotFrameworkAuthentication cloudEnvironmentMock = mock(BotFrameworkAuthentication.class);
        when(cloudEnvironmentMock.authenticateRequest(any (Activity.class), any (String.class))).thenReturn(CompletableFuture.completedFuture(authenticateRequestResult));
        when(cloudEnvironmentMock.createConnectorFactory(any (ClaimsIdentity.class))).thenReturn(new TestConnectorFactory());
        when(cloudEnvironmentMock.createUserTokenClient(any (ClaimsIdentity.class))).thenReturn(CompletableFuture.completedFuture(userTokenClient));

        ConnectorFactoryBot bot = new ConnectorFactoryBot();

        // Act
        CloudAdapter adapter = new CloudAdapter(cloudEnvironmentMock);
        adapter.processIncomingActivity("", createMessageActivity(), bot);

        // Assert
        Assert.assertEquals("audience", bot.authorization);
        Assert.assertEquals(claimsIdentity, bot.identity);
        Assert.assertEquals(userTokenClient, bot.userTokenClient);
        Assert.assertTrue(bot.connectorClient != null);
        Assert.assertTrue(bot.botCallbackHandler != null);
    }

    @Test
    public void cloudAdapterContinueConversation() {
        ClaimsIdentity claimsIdentity = new ClaimsIdentity("");

        AuthenticateRequestResult authenticateRequestResult = new AuthenticateRequestResult();
        authenticateRequestResult.setClaimsIdentity(claimsIdentity);
        authenticateRequestResult.setConnectorFactory(new TestConnectorFactory());
        authenticateRequestResult.setAudience("audience");
        authenticateRequestResult.setCallerId("callerId");

        TestUserTokenClient userTokenClient = new TestUserTokenClient("appId");

        BotFrameworkAuthentication cloudEnvironmentMock = mock(BotFrameworkAuthentication.class);
        when(cloudEnvironmentMock.authenticateRequest(any (Activity.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(authenticateRequestResult));
        when(cloudEnvironmentMock.createConnectorFactory(any (ClaimsIdentity.class))).thenReturn(new TestConnectorFactory());
        when(cloudEnvironmentMock.createUserTokenClient(any (ClaimsIdentity.class))).thenReturn(CompletableFuture.completedFuture(userTokenClient));

        // NOTE: present in C# but not used
        ConnectorFactoryBot bot = new ConnectorFactoryBot();

        String expectedServiceUrl = "http://serviceUrl";

        ConversationAccount conversationAccount = new ConversationAccount();
        conversationAccount.setId("conversation Id");
        Activity continuationActivity = new Activity(ActivityTypes.EVENT);
        ConversationReference conversationReference = new ConversationReference();
        conversationReference.setServiceUrl(expectedServiceUrl);
        conversationReference.setConversation(conversationAccount);

        String actualServiceUrl1 = "";
        String actualServiceUrl2 = "";
        String actualServiceUrl3 = "";
        String actualServiceUrl4 = "";
        String actualServiceUrl5 = "";
        String actualServiceUrl6 = "";

        BotCallbackHandler callback1 = (t) -> {
            actualServiceUrl1 = t.getActivity().getServiceUrl();
            return CompletableFuture.completedFuture(null);
        };

        BotCallbackHandler callback2 = (t) -> {
            actualServiceUrl2 = t.getActivity().getServiceUrl();
            return CompletableFuture.completedFuture(null);
        };

        BotCallbackHandler callback3 = (t) -> {
            actualServiceUrl3 = t.getActivity().getServiceUrl();
            return CompletableFuture.completedFuture(null);
        };

        BotCallbackHandler callback4 = (t) -> {
            actualServiceUrl4 = t.getActivity().getServiceUrl();
            return CompletableFuture.completedFuture(null);
        };

        BotCallbackHandler callback5 = (t) -> {
            actualServiceUrl5 = t.getActivity().getServiceUrl();
            return CompletableFuture.completedFuture(null);
        };

        BotCallbackHandler callback6 = (t) -> {
            actualServiceUrl6 = t.getActivity().getServiceUrl();
            return CompletableFuture.completedFuture(null);
        };

        // Act
        CloudAdapter adapter = new CloudAdapter(cloudEnvironmentMock);
        adapter.continueConversation("botAppId", continuationActivity, callback1);
        adapter.continueConversation(claimsIdentity, continuationActivity, callback2);
        adapter.continueConversation(claimsIdentity, continuationActivity, "audience", callback3);
        adapter.continueConversation("botAppId", conversationReference, callback4);
        adapter.continueConversation(claimsIdentity, conversationReference, callback5);
        adapter.continueConversation(claimsIdentity, conversationReference, "audience", callback6);

        // Assert
        Assert.assertEquals(expectedServiceUrl, actualServiceUrl1);
        Assert.assertEquals(expectedServiceUrl, actualServiceUrl2);
        Assert.assertEquals(expectedServiceUrl, actualServiceUrl3);
        Assert.assertEquals(expectedServiceUrl, actualServiceUrl4);
        Assert.assertEquals(expectedServiceUrl, actualServiceUrl5);
        Assert.assertEquals(expectedServiceUrl, actualServiceUrl6);
    }

    private static Activity createMessageActivity() {
        return createMessageActivity("userId", "channelId", "conversationId", "botId", "relatesToActivityId");
    }

    private static Activity createMessageActivity(String userId, String channelId, String conversationId, String recipient, String relatesToActivityId) {
        ConversationAccount conversationAccount = new ConversationAccount();
        conversationAccount.setId(conversationId);

        ChannelAccount fromChannelAccount = new ChannelAccount();
        fromChannelAccount.setId(userId);

        ChannelAccount toChannelAccount = new ChannelAccount();
        toChannelAccount.setId(recipient);

        ConversationReference conversationReference = new ConversationReference();
        conversationReference.setActivityId(relatesToActivityId);

        Activity activity = new Activity(ActivityTypes.MESSAGE);
        activity.setText("hi");
        activity.setServiceUrl("http://localhost");
        activity.setChannelId(channelId);
        activity.setConversation(conversationAccount);
        activity.setFrom(fromChannelAccount);
        activity.setLocale("locale");
        activity.setRecipient(toChannelAccount);
        activity.setRelatesTo(conversationReference);

        return activity;
    }

    private static Stream createBadRequestStream() {
        MemoryStream stream = new MemoryStream();
        StreamWriter textWriter = new StreamWriter(stream);
        textWriter.Write("this.is.not.json");
        textWriter.Flush();
        stream.Seek(0, SeekOrigin.Begin);
        return stream;
    }

    private static HttpResponseMessage createInternalHttpResponse() {
        var response = new HttpResponseMessage(HttpStatusCode.OK);
        response.Content = new StringContent(new JObject { { "id", "SendActivityId" } }.ToString());
        return response;
    }

    private static Activity createInvokeActivity() {
        Activity activity = new Activity(ActivityTypes.INVOKE);
        activity.setServiceUrl("http://localhost");
        return activity;
    }

    private class ConnectorFactoryBot implements Bot {
        private Identity identity;
        private ConnectorClient connectorClient;
        private UserTokenClient userTokenClient;
        private BotCallbackHandler botCallbackHandler;
        private String oAuthScope;
        private String authorization;

        public Identity getIdentity() {
            return identity;
        }

        // NOTE: probably not needed:
        public ConnectorClient getConnectorClient() {
            return connectorClient;
        }

        public UserTokenClient getUserTokenClient() {
            return userTokenClient;
        }

        public BotCallbackHandler getBotCallbackHandler() {
            return botCallbackHandler;
        }

        public String getoAuthScope() {
            return oAuthScope;
        }

        public String getAuthorization() {
            return authorization;
        }
        //

        public CompletableFuture<Void> onTurn(TurnContext turnContext) {
            // verify the bot-framework protocol TurnState has been setup by the adapter
            identity = turnContext.getTurnState().get("BotIdentity");
            connectorClient = turnContext.getTurnState().get(ConnectorClient.class);
            userTokenClient = turnContext.getTurnState().get(UserTokenClient.class);
            botCallbackHandler = turnContext.getTurnState().get(BotCallbackHandler.class);
            oAuthScope = turnContext.getTurnState().get("Microsoft.Bot.Builder.BotAdapter.OAuthScope");

            ConnectorFactory connectorFactory = turnContext.getTurnState().get(ConnectorFactory.class);

            connectorFactory.create("http://localhost/originalServiceUrl", oAuthScope).thenCompose(connector -> {
                    OkHttpClient.Builder builder = new OkHttpClient.Builder();
                    connector.credentials().applyCredentialsFilter(builder);
                    OkHttpClient client = builder.build();
                    client.newCall(RequestBody.create());

                    authorization = client // request.Headers.Authorization;
                }
            );
        }
    }

    private class UserTokenClientBot implements Bot {
        private String connectionName;

        public UserTokenClientBot(String withConnectionName) {
            connectionName = withConnectionName;
        }

        public CompletableFuture<Void> onTurn(TurnContext turnContext) {
            // in the product the following calls are made from within the sign-in prompt begin and continue methods

            UserTokenClient userTokenClient = turnContext.getTurnState().get(UserTokenClient.class);

            userTokenClient.exchangeToken(
                turnContext.getActivity().getFrom().getId(),
                connectionName,
                turnContext.getActivity().getChannelId(),
                new TokenExchangeRequest() { }).thenApply(result -> null);

            userTokenClient.getAadTokens(
                turnContext.getActivity().getFrom().getId(),
                connectionName,
                Arrays.asList("x", "y"),
                turnContext.getActivity().getChannelId()).thenApply(result -> null);

            userTokenClient.getSignInResource(
                connectionName,
                turnContext.getActivity(),
                "finalRedirect").thenApply(result -> null);

            userTokenClient.getTokenStatus(
                turnContext.getActivity().getFrom().getId(),
                turnContext.getActivity().getChannelId(),
                "includeFilter").thenApply(result -> null);

            userTokenClient.getUserToken(
                turnContext.getActivity().getFrom().getId(),
                connectionName,
                turnContext.getActivity().getChannelId(),
                "magicCode").thenApply(result -> null);

            // in the product code the sign-out call is generally run as a general intercept before any dialog logic
            return userTokenClient.signOutUser(
                turnContext.getActivity().getFrom().getId(),
                connectionName,
                turnContext.getActivity().getChannelId());
        }
    }

    private class TestUserTokenClient extends UserTokenClient {
        private String appId;

        public TestUserTokenClient(String withAppId) {
            appId = withAppId;
        }

        private Map<String, Object[]> record = new HashMap<>();

        public Object[] getRecord(String key) {
            return record.get(key);
        }

        @Override
        public CompletableFuture<TokenResponse> exchangeToken(String userId, String connectionName, String channelId, TokenExchangeRequest exchangeRequest) {
            capture("exchangeToken", userId, connectionName, channelId, exchangeRequest);
            return CompletableFuture.completedFuture(new TokenResponse() { });
        }

        @Override
        public CompletableFuture<SignInResource> getSignInResource(String connectionName, Activity activity, String finalRedirect) {
            String state = createTokenExchangeState(appId, connectionName, activity);
            capture("getSignInResource", state, finalRedirect);
            return CompletableFuture.completedFuture(new SignInResource() { });
        }

        @Override
        public CompletableFuture<List<TokenStatus>> getTokenStatus(String userId, String channelId, String includeFilter) {
            capture("getTokenStatus", userId, channelId, includeFilter);
            return CompletableFuture.completedFuture(Arrays.asList(new TokenStatus[0]));
        }

        @Override
        public CompletableFuture<Map<String, TokenResponse>> getAadTokens(String userId, String connectionName, List<String> resourceUrls, String channelId) {
            capture("getAadTokens", userId, connectionName, resourceUrls, channelId);
            return CompletableFuture.completedFuture(new HashMap<String, TokenResponse>() { });
        }

        @Override
        public CompletableFuture<TokenResponse> getUserToken(String userId, String connectionName, String channelId, String magicCode) {
            capture("getUserToken", userId, connectionName, channelId, magicCode);
            return CompletableFuture.completedFuture(new TokenResponse());
        }

        @Override
        public CompletableFuture<Void> signOutUser(String userId, String connectionName, String channelId) {
            capture("signOutUser", userId, connectionName, channelId);
            return CompletableFuture.completedFuture(null);
        }

        private void capture(String name, Object... args) {
            record.put(name, args);
        }
    }

    private class InvokeResponseBot implements Bot {

        @Override
        public CompletableFuture<Void> onTurn(TurnContext turnContext) {
            return turnContext.sendActivity(createInvokeResponseActivity()).thenApply(result -> null);
        }

        private Activity createInvokeResponseActivity() {
            Activity activity = new Activity(ActivityTypes.INVOKE_RESPONSE);
            InvokeResponse invokeResponse = new InvokeResponse(200, new Object[]{ "quite.honestly", "im.feeling.really.attacked.right.now" });
            activity.setValue(invokeResponse);

            return activity;
        }
    }

    private class MessageBot implements Bot {
        public CompletableFuture<Void> onTurn(TurnContext turnContext) {
            return turnContext.sendActivity(MessageFactory.text("rage.rage.against.the.dying.of.the.light")).thenApply(result -> null);
        }
    }

    private class TestCredentials implements ServiceClientCredentials {
        private String testToken;

        public TestCredentials(String withTestToken) {
            testToken = withTestToken;
        }

        @Override
        public void applyCredentialsFilter(OkHttpClient.Builder clientBuilder) {
            // NOTE: required by inheritance
        }
    }

    private class TestConnectorFactory extends ConnectorFactory {
        @Override
        public CompletableFuture<ConnectorClient> create(String serviceUrl, String audience) {
            TestCredentials credentials = new TestCredentials(StringUtils.isNotBlank(audience) ? audience : "test-token");
            return CompletableFuture.completedFuture(new ConnectorClient(new Uri(serviceUrl), credentials, null, true));
        }
    }
}
