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
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ConversationAccount;
import com.microsoft.bot.schema.ConversationReference;
import com.microsoft.bot.schema.InvokeResponse;
import com.microsoft.bot.schema.SignInResource;
import com.microsoft.bot.schema.TokenExchangeRequest;
import com.microsoft.bot.schema.TokenExchangeState;
import com.microsoft.bot.schema.TokenResponse;
import com.microsoft.bot.schema.TokenStatus;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.omg.IOP.Encoding;

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
        /*HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(this.createMessageActivityStream());
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock);

        HttpResponse httpResponseMock = mock(HttpResponse.class);*/

        Bot botMock = mock(Bot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture());

        Activity activity = new Activity("");

        // Act
        CloudAdapter adapter = new CloudAdapter();
        adapter.processIncomingActivity("", activity, botMock);

        verify(botMock, atMostOnce()).onTurn(any (TurnContext.class));
    }

    @Test
    public void invokeActivity() {
        // Arrange

        InvokeResponseBot botMock = mock(InvokeResponseBot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture());

        Activity activity = new Activity("");

        // Act
        CloudAdapter adapter = new CloudAdapter();
        adapter.processIncomingActivity(, activity, botMock);

        verify(botMock, atMostOnce()).onTurn(any (TurnContext.class));
    }

    public void webSocketRequestShouldCallAuthenticateStreamingRequest() {
        // Note this test only checks that a GET request will result in an auth call and a socket accept
        // it doesn't valid that activities over that socket get to the bot or back

        // Arrange
        WebSock
    }

    @Test
    public void messageActivityWithHttpClient() {
        // Arrange
        //
        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(createMessageActivityStream());
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock);

        HttpResponse httpResponseMock = mock(HttpResponse.class);

        /*var mockHttpMessageHandler = new Mock<HttpMessageHandler>();
        mockHttpMessageHandler.Protected()
            .Setup<Task<HttpResponseMessage>>("SendAsync", ItExpr.IsAny<HttpRequestMessage>(), ItExpr.IsAny<CancellationToken>())
                .Returns((HttpRequestMessage request, CancellationToken cancellationToken) => Task.FromResult(CreateInternalHttpResponse()));*/

        OkHttpClient httpClient = new OkHttpClient();



        Bot bot = new MessageBot();

        // Act
        BotFrameworkAuthentication cloudEnvironment = BotFrameworkAuthenticationFactory.create(null, false, null, null, null, null, null, null, null, new PasswordServiceClientCredentialFactory(), new AuthenticationConfiguration(), httpClientFactoryMock, null);
        CloudAdapter adapter = new CloudAdapter(cloudEnvironment);
        adapter.processIncomingActivity(, activity, bot);

        // Assert
        verify()
    }

    /*/ NOPE
    public void constructorWithConfiguration() {

        Map<String, String> appSettings = new HashMap<>();
        static {
            appSettings.put("MicrosoftAppId", "appId");
            appSettings.put("MicrosoftAppPassword", "appPassword");
            appSettings.put("ChannelService", GovernmentAuthenticationConstants.CHANNELSERVICE);
        }

        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(appSettings)
            .Build();

        _ = new CloudAdapter(configuration);

         TODO: work out what might be a reasonable side effect
    }*/

    @Test
    public void badRequest() {
        // Arrange
        var headerDictionaryMock = new Mock<IHeaderDictionary>();
        headerDictionaryMock.Setup(h => h[It.Is<string>(v => v == "Authorization")]).Returns<string>(null);

        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(createBadRequestStream());
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock);

        HttpResponse httpResponseMock = mock(HttpResponse.class);
        setup
        httpResponseMock.SetupProperty(x => x.StatusCode);

        Bot botMock = mock(Bot.class);
        when(botMock.onTurn(any (TurnContext.class))).thenReturn(CompletableFuture.completedFuture());

        Activity activity = new Activity("");

        // Act
        CloudAdapter adapter = new CloudAdapter();
        adapter.processIncomingActivity(, activity, botMock);

        // Assert
        verify(botMock, never()).onTurn(any (TurnContext.class));
        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, httpResponseMock.statusCode());
    }

    @Test
    public void injectCloudEnvironment() {
        // Arrange
        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(createMessageActivityStream());
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock);

        HttpResponse httpResponseMock = mock(HttpResponse.class);

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

        OkHttpClient httpClient = mock(OkHttpClient.class);

        // Act
        CloudAdapter adapter = new CloudAdapter(cloudEnvironmentMock);
        adapter.processIncomingActivity("", activity, botMock);

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

        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(createMessageActivityStream(userId, channelId, conversationId, recipientId, relatesToActivityId));
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock.Object);

        HttpResponse httpResponseMock = mock(HttpResponse.class);

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
        CloudAdapter adapter = new CloudAdapter(cloudEnvironmentMock);
        adapter.processIncomingActivity("", activity, bot);

        // Assert
        Object[] args_ExchangeToken = userTokenClient.getRecord("ExchangeTokenAsync"); // CHECK THIS STRING NAME
        Assert.assertEquals(userId, (String) args_ExchangeToken[0]);
        Assert.assertEquals(connectionName, (String)args_ExchangeToken[1]);
        Assert.assertEquals(channelId, (String) args_ExchangeToken[2]);
        Assert.assertEquals("TokenExchangeRequest", args_ExchangeToken[3].getClass().getName());

        Object[] args_GetAadTokens = userTokenClient.getRecord("GetSignInResourceAsync");
        Assert.assertEquals(userId, (String) args_GetAadTokens[0]);
        Assert.assertEquals(connectionName, (String) args_GetAadTokens[1]);
        Assert.assertEquals("x", ((String[]) args_GetAadTokens[2])[0]);
        Assert.assertEquals("y", ((String[]) args_GetAadTokens[2])[1]);

        Assert.assertEquals(channelId, (String) args_GetAadTokens[3]);

        Object[] args_GetSignInResource = userTokenClient.getRecord("GetSignInResourceAsync");

        // this code is testing the internal CreateTokenExchangeState function by doing the work in reverse
        String state = (String) args_GetSignInResource[0];
        String json = Encoding.UTF8.GetString(Convert.FromBase64String(state));
        TokenExchangeState tokenExchangeState = JsonConvert.DeserializeObject<TokenExchangeState>(json);
        Assert.assertEquals(connectionName, tokenExchangeState.getConnectionName());
        Assert.assertEquals(appId, tokenExchangeState.getMsAppId());
        Assert.assertEquals(conversationId, tokenExchangeState.getConversation().getId());
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
        HttpRequest httpRequestMock = mock(HttpRequest.class);
        when(httpRequestMock.httpMethod()).thenReturn(HttpMethod.POST);
        when(httpRequestMock.body()).thenReturn(this.createMessageActivityStream());
        when(httpRequestMock.headers()).thenReturn(headerDictionaryMock.Object);

        HttpResponse httpResponseMock = mock(HttpResponse.class);

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
        adapter.processIncomingActivity("", activity, bot);

        // Assert
        Assert.assertEquals("audience", bot.authorization.Parameter);
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
        when(cloudEnvironmentMock.authenticateRequest()).thenReturn(CompletableFuture.completedFuture(authenticateRequestResult));
        when(cloudEnvironmentMock.createConnectorFactory()).thenReturn(new TestConnectorFactory());
        when(cloudEnvironmentMock.createUserTokenClient()).thenReturn(CompletableFuture.completedFuture(userTokenClient));

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

    private static Stream createMessageActivityStream() {
        return createMessageActivityStream("userId", "channelId", "conversationId", "botId", "relatesToActivityId");
    }

    private static Stream createMessageActivityStream(String userId, String channelId, String conversationId, String recipient, String relatesToActivityId) {

    }

    private static Stream createBadRequestStream()
    {
        MemoryStream stream = new MemoryStream();
        StreamWriter textWriter = new StreamWriter(stream);
        textWriter.Write("this.is.not.json");
        textWriter.Flush();
        stream.Seek(0, SeekOrigin.Begin);
        return stream;
    }

    private static HttpResponseMessage CreateInternalHttpResponse()
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK);
        response.Content = new StringContent(new JObject { { "id", "SendActivityId" } }.ToString());
        return response;
    }

    private static Stream CreateInvokeActivityStream()
    {
        return CreateStream(new Activity { Type = ActivityTypes.Invoke, ServiceUrl = "http://localhost" });
    }

    private static Stream createStream(Activity activity) {
        String json = ;

    }

    private class ConnectorFactoryBot implements Bot {
        private Identity identity;
        private ConnectorClient connectorClient;
        private UserTokenClient userTokenClient;
        private BotCallbackHandler botCallbackHandler;
        private String oAuthScope;
        private AuthenticationHeaderValue authorization;

        public Identity getIdentity() {
            return identity;
        }

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

        public AuthenticationHeaderValue getAuthorization() {
            return authorization;
        }

        public CompletableFuture<Void> onTurn(TurnContext turnContext) {
            // verify the bot-framework protocol TurnState has been setup by the adapter
            identity = turnContext.getTurnState().get("BotIdentity");
            connectorClient = turnContext.getTurnState().get(ConnectorClient.class);
            userTokenClient = turnContext.getTurnState().get(UserTokenClient.class);
            botCallbackHandler = turnContext.getTurnState().get(BotCallbackHandler.class);
            oAuthScope = turnContext.getTurnState().get("Microsoft.Bot.Builder.BotAdapter.OAuthScope");

            ConnectorFactory connectorFactory = turnContext.getTurnState().get(ConnectorFactory.class);

            connectorFactory.create("http://localhost/originalServiceUrl", oAuthScope).thenCompose(connector -> {
                    var request = new HttpRequestMessage();
                    connector.Credentials.ProcessHttpRequestAsync(request).thenApply(result -> null);
                    Authorization = request.Headers.Authorization;
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

        private Dictionary<String, Object[]> record = new Dictionary<String, Object[]>();

        public Object[] getRecord(String key) {
            return record.get(key);
        }

        @Override
        public CompletableFuture<TokenResponse> exchangeToken(String userId, String connectionName, String channelId, TokenExchangeRequest exchangeRequest) {
            capture(MethodBase.GetCurrentMethod().Name, userId, connectionName, channelId, exchangeRequest);
            return CompletableFuture.completedFuture(new TokenResponse { });
        }

        @Override
        public CompletableFuture<Dictionary<String, TokenResponse>> getAadTokens(String userId, String connectionName, String[] resourceUrls, String channelId) {
            capture(MethodBase.GetCurrentMethod().Name, userId, connectionName, resourceUrls, channelId);
            return CompletableFuture.completedFuture(new Dictionary<String, TokenResponse> { });
        }

        @Override
        public CompletableFuture<SignInResource> getSignInResource(String connectionName, Activity activity, String finalRedirect) {
            String state = createTokenExchangeState(appId, connectionName, activity);
            capture(MethodBase.GetCurrentMethod().Name, state, finalRedirect);
            return CompletableFuture(new SignInResource { });
        }

        @Override
        public CompletableFuture<TokenStatus[]> getTokenStatus(String userId, String channelId, String includeFilter) {
            capture(MethodBase.GetCurrentMethod().Name, userId, channelId, includeFilter);
            return CompletableFuture.completedFuture(new TokenStatus[0]);
        }

        @Override
        public CompletableFuture<Map<String, TokenResponse>> getAadTokens(String userId, String connectionName, List<String> resourceUrls, String channelId) {
            return null;
        }

        @Override
        public CompletableFuture<TokenResponse> getUserToken(String userId, String connectionName, String channelId, String magicCode) {
            capture(MethodBase.GetCurrentMethod().Name, userId, connectionName, channelId, magicCode);
            return CompletableFuture.completedFuture(new TokenResponse());
        }

        @Override
        public CompletableFuture<Void> signOutUser(String userId, String connectionName, String channelId) {
            capture(MethodBase.GetCurrentMethod().Name, userId, connectionName, channelId);
            return CompletableFuture.completedFuture(null);
        }

        private void capture(String name, Object[] args) {
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

        public CompletableFuture<Void> processHttpRequest(HttpRequestMessage request) {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", testToken);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void applyCredentialsFilter(OkHttpClient.Builder clientBuilder) {
            // Required by inheritance
        }
    }

    private class TestConnectorFactory extends ConnectorFactory {
        @Override
        public CompletableFuture<ConnectorClient> create(String serviceUrl, String audience) {
            TestCredentials credentials = new TestCredentials(StringUtils.isNotBlank(audience) ? audience : "test-token");
            return CompletableFuture.completedFuture(new ConnectorClient(new Uri(serviceUrl), credentials, null, disposeHttpClient: true));
        }
    }
}
