package com.microsoft.bot.builder;

import com.microsoft.bot.builder.skills.BotFrameworkSkill;
import com.microsoft.bot.builder.skills.CloudSkillHandler;
import com.microsoft.bot.builder.skills.SkillConversationIdFactoryBase;
import com.microsoft.bot.builder.skills.SkillConversationIdFactoryOptions;
import com.microsoft.bot.builder.skills.SkillConversationReference;
import com.microsoft.bot.connector.authentication.AuthenticationConstants;
import com.microsoft.bot.connector.authentication.BotFrameworkAuthentication;
import com.microsoft.bot.connector.authentication.ClaimsIdentity;
import com.microsoft.bot.restclient.serializer.JacksonAdapter;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.CallerIdConstants;
import com.microsoft.bot.schema.ConversationAccount;
import com.microsoft.bot.schema.ConversationReference;
import com.microsoft.bot.schema.ResourceResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class CloudSkillHandlerTests {

    private final String testSkillId = UUID.randomUUID().toString().replace("-", "");
    private final String testAuthHeader = ""; // Empty since claims extraction is being mocked

    public void testSendAndReplyToConversation(String activityType, String replyToId) {
        // Arrange
        CloudSkillHandlerTestMocks mockObjects = new CloudSkillHandlerTestMocks();
        Activity activity = new Activity(activityType);
        activity.setReplyToId(replyToId);
        mockObjects.createAndApplyConversationId(activity).thenCompose(conversationId -> {
            // Act
            CloudSkillHandler sut = new CloudSkillHandler(
                mockObjects.getAdapter(),
                mockObjects.getBot(),
                mockObjects.getConversationIdFactory(),
                mockObjects.getAuth());

            ResourceResponse response = replyToId == null ?
                sut.handleSendToConversation(testAuthHeader, conversationId, activity).join() :
                sut.handleReplyToActivity(testAuthHeader, conversationId, replyToId, activity).join();

            // Assert
            // Assert the turnContext
            Assert.assertEquals(CallerIdConstants.BOT_TO_BOT_PREFIX.concat(testSkillId), mockObjects.getTurnContext().getActivity().getCallerId());
            Assert.assertNotNull(mockObjects.getTurnContext().getTurnState().get(CloudSkillHandler.SKILL_CONVERSATION_REFERENCE_KEY));

            // Assert based on activity type,
            if (activityType.equals(ActivityTypes.MESSAGE)) {
                // Should be sent to the channel and not to the bot.
                Assert.assertNotNull(mockObjects.getChannelActivity());
                Assert.assertNull(mockObjects.getBotActivity());

                // We should get the resourceId returned by the mock.
                Assert.assertEquals("resourceId", response.getId());

                // Assert the activity sent to the channel.
                Assert.assertEquals(activityType, mockObjects.getChannelActivity().getType());
                Assert.assertNull(mockObjects.getChannelActivity().getCallerId());
                Assert.assertEquals(replyToId, mockObjects.getChannelActivity().getReplyToId());
            } else {
                // Should be sent to the bot and not to the channel.
                Assert.assertNull(mockObjects.getChannelActivity());
                Assert.assertNotNull(mockObjects.getBotActivity());

                // If the activity is bounced back to the bot we will get a GUID and not the mocked resourceId.
                Assert.assertNotEquals("resourceId", response.getId());

                // Assert the activity sent back to the bot.
                Assert.assertEquals(activityType, mockObjects.getBotActivity().getType());
                Assert.assertEquals(replyToId, mockObjects.getBotActivity().getReplyToId());
            }
            return null;
        });
    }

    public void TestCommandActivities(String commandActivityType, String name, String replyToId) {
        // Arrange
        CloudSkillHandlerTestMocks mockObjects = new CloudSkillHandlerTestMocks();
        Activity activity = new Activity(commandActivityType);
        activity.setName(name);
        activity.setReplyToId(replyToId);

        mockObjects.createAndApplyConversationId(activity).thenCompose(conversationId -> {
            // Act
            CloudSkillHandler sut = new CloudSkillHandler(
                mockObjects.getAdapter(),
                mockObjects.getBot(),
                mockObjects.getConversationIdFactory(),
                mockObjects.getAuth());

            ResourceResponse response = replyToId == null ?
                sut.handleSendToConversation(testAuthHeader, conversationId, activity).join() :
                sut.handleReplyToActivity(testAuthHeader, conversationId, replyToId, activity).join();

            // Assert
            // Assert the turnContext
            Assert.assertEquals(CallerIdConstants.BOT_TO_BOT_PREFIX.concat(testSkillId), mockObjects.getTurnContext().getActivity().getCallerId());
            Assert.assertNotNull(mockObjects.getTurnContext().getTurnState().get(CloudSkillHandler.SKILL_CONVERSATION_REFERENCE_KEY));
            if (StringUtils.startsWith(name, "application/")) {
                // Should be sent to the channel and not to the bot.
                Assert.assertNotNull(mockObjects.getChannelActivity());
                Assert.assertNotNull(mockObjects.getBotActivity());

                // We should get the resourceId returned by the mock.
                Assert.assertEquals("resourceId", response.getId());
            } else {
                // Should be sent to the bot and not to the channel.
                Assert.assertNotNull(mockObjects.getChannelActivity());
                Assert.assertNotNull(mockObjects.getBotActivity());

                // If the activity is bounced back to the bot we will get a GUID and not the mocked resourceId.
                Assert.assertNotEquals("resourceId", response.getId());
            }
            return null;
        });
    }

    @Test
    public void testDeleteActivity() {
        // Arrange
        CloudSkillHandlerTestMocks mockObjects = new CloudSkillHandlerTestMocks();
        Activity activity = new Activity(ActivityTypes.MESSAGE);
        mockObjects.createAndApplyConversationId(activity).thenCompose(conversationId -> {

            String activityToDelete = UUID.randomUUID().toString();

            // Act
            CloudSkillHandler sut = new CloudSkillHandler(mockObjects.getAdapter(), mockObjects.getBot(), mockObjects.getConversationIdFactory(), mockObjects.getAuth());
            sut.handleDeleteActivity(testAuthHeader, conversationId, activityToDelete).join();

            // Assert
            Assert.assertNotNull(mockObjects.getTurnContext().getTurnState().get(CloudSkillHandler.SKILL_CONVERSATION_REFERENCE_KEY));
            Assert.assertEquals(activityToDelete, mockObjects.getActivityIdToDelete());
        });
    }

    @Test
    public void testUpdateActivity() {
        // Arrange
        CloudSkillHandlerTestMocks mockObjects = new CloudSkillHandlerTestMocks();
        Activity activity = new Activity(ActivityTypes.MESSAGE);
        activity.setText(String.format("TestUpdate %s.", LocalDateTime.now()));
        mockObjects.createAndApplyConversationId(activity).thenCompose(conversationId -> {
            String activityToUpdate = UUID.randomUUID().toString();

            // Act
            CloudSkillHandler sut = new CloudSkillHandler(mockObjects.getAdapter(), mockObjects.getBot(), mockObjects.getConversationIdFactory(), mockObjects.getAuth());
            sut.handleUpdateActivity(testAuthHeader, conversationId, activityToUpdate, activity).thenCompose(response -> {
                Assert.assertEquals("resourceId", response.getId());
                return null;
            });

            // Assert
            Assert.assertNotNull(mockObjects.getTurnContext().getTurnState().get(CloudSkillHandler.SKILL_CONVERSATION_REFERENCE_KEY));
            Assert.assertEquals(activityToUpdate, mockObjects.getTurnContext().getActivity().getId());
            Assert.assertEquals(activity.getText(), mockObjects.getUpdateActivity().getText());
            return null;
        });
    }

    private class CloudSkillHandlerTestMocks {
        private final String TestBotId = UUID.randomUUID().toString().replace("-", "");
        private static final String TestBotEndpoint = "http://testbot.com/api/messages";
        private static final String TestSkillEndpoint = "http://testskill.com/api/messages";

        private final SkillConversationIdFactoryBase conversationIdFactory;
        private final BotAdapter adapter;
        private final BotFrameworkAuthentication auth;
        private final Bot bot;
        private TurnContext turnContext;
        private Activity channelActivity;
        private Activity botActivity;
        private Activity updateActivity;
        private String activityToDelete;

        public CloudSkillHandlerTestMocks() {
            adapter = createMockAdapter();
            auth = createMockBotFrameworkAuthentication();
            bot = createMockBot();
            conversationIdFactory = new TestSkillConversationIdFactory();
        }

        public SkillConversationIdFactoryBase getConversationIdFactory() {
            return conversationIdFactory;
        }

        public BotAdapter getAdapter() {
            return adapter;
        }

        public BotFrameworkAuthentication getAuth() {
            return auth;
        }

        public Bot getBot() {
            return bot;
        }

        // Gets the TurnContext created to call the bot.
        public TurnContext getTurnContext() {
            return turnContext;
        }

        /**
         * Gets the Activity sent to the channel.
         * @return
         */
        public Activity getChannelActivity() {
            return channelActivity;
        }

        /**
         * Gets the Activity sent to the Bot.
         * @return
         */
        public Activity getBotActivity() {
            return botActivity;
        }

        /**
         * Gets the update activity.
         * @return
         */
        public Activity getUpdateActivity() {
            return updateActivity;
        }

        /**
         * Gets the Activity sent to the Bot.
         * @return
         */
        public String getActivityIdToDelete() {
            return activityToDelete;
        }

        public CompletableFuture<String> createAndApplyConversationId(Activity activity) {
            ConversationReference conversationReference = new ConversationReference();
            ConversationAccount conversationAccount = new ConversationAccount();
            conversationAccount.setId(TestBotId);
            conversationReference.setConversation(conversationAccount);
            conversationReference.setServiceUrl(TestBotEndpoint);

            activity.applyConversationReference(conversationReference);

            BotFrameworkSkill skill = new BotFrameworkSkill();
            skill.setAppId(testSkillId);
            skill.setId("skill");

            try {
                skill.setSkillEndpoint(new URI(TestSkillEndpoint));
            }
            catch (URISyntaxException e) {
            }

            SkillConversationIdFactoryOptions options = new SkillConversationIdFactoryOptions();
            options.setFromBotOAuthScope(TestBotId);
            options.setFromBotId(TestBotId);
            options.setActivity(activity);
            options.setBotFrameworkSkill(skill);

            return getConversationIdFactory().createSkillConversationId(options);
        }

        private BotAdapter createMockAdapter() {
            BotAdapter adapter = Mockito.mock(BotAdapter.class);

            // Mock the adapter ContinueConversationAsync method
            // This code block catches and executes the custom bot callback created by the service handler.
            Mockito.when(
                adapter.continueConversation(
                    Mockito.any (ClaimsIdentity.class),
                    Mockito.any (ConversationReference.class),
                    Mockito.any(String.class),
                    Mockito.any(BotCallbackHandler.class))
            ).thenAnswer(
                new Answer<Void>() {
                    Void answer(InvocationOnMock invocation) {
                        TurnContext turnContext = new TurnContextImpl();
                        BotCallbackHandler callback = invocation.getArgument(3);
                        callback.invoke(turnContext);
                        return null;
                    }
                }
            );


            // Mock the adapter SendActivitiesAsync method (this for the cases where activity is sent back to the parent or channel)
            Mockito.when(
                adapter.sendActivities(
                    Mockito.any(TurnContext.class),
                    Mockito.any(List.class))
            ).thenAnswer(
                new Answer<Void>() {
                    Void answer(InvocationOnMock invocation) {
                        // Capture the activity sent to the channel
                        List<Activity> activities = invocation.getArgument(1);
                        channelActivity = activities.get(0);

                        // Do nothing, we don't want the activities sent to the channel in the tests.
                        return null;
                    }
                }
            ).thenReturn(CompletableFuture.completedFuture(new ResourceResponse[]{new ResourceResponse("resourceId")}));


            // Mock the DeleteActivityAsync method
            Mockito.when(
                adapter.deleteActivity(
                    Mockito.any(TurnContext.class),
                    Mockito.any(ConversationReference.class))
            ).thenAnswer(
                    new Answer<Void>() {
                        Void answer(InvocationOnMock invocation) {
                            // Capture the activity id to delete so we can assert it.
                            activityToDelete = invocation.getArgument(1);
                            return null;
                        }
                    }
                );

            // Mock the UpdateActivityAsync method
            Mockito.when(
                adapter.updateActivity(
                    Mockito.any(TurnContext.class),
                    Mockito.any(Activity.class))
            ).thenAnswer(
                new Answer<Void>() {
                    Void answer(InvocationOnMock invocation) {
                        updateActivity = invocation.getArgument(1);
                        return null;
                    }
                }).thenReturn(CompletableFuture.completedFuture(new ResourceResponse("resourceId")));

            return adapter;
        }

        private Bot createMockBot() {
            Bot bot = Mockito.mock(Bot.class);
            Mockito.when(
                bot.onTurn(Mockito.any(TurnContext.class))
            ).thenAnswer(
                new Answer<Void>() {
                    Void answer(InvocationOnMock invocation) {
                        botActivity = invocation.getArgument(0);
                        return null;
                    }
                }
            );

            return bot;
        }

        private BotFrameworkAuthentication createMockBotFrameworkAuthentication() {
            BotFrameworkAuthentication auth = Mockito.mock(BotFrameworkAuthentication.class);

            Mockito.when(
                auth.authenticateChannelRequest(Mockito.any(String.class))
            ).thenAnswer(
                new Answer<ClaimsIdentity>() {
                    ClaimsIdentity answer(InvocationOnMock invocation) {
                        HashMap<String, String> claims = new HashMap<String, String>();
                        claims.put(AuthenticationConstants.AUDIENCE_CLAIM, TestBotId);
                        claims.put(AuthenticationConstants.APPID_CLAIM, testSkillId);
                        claims.put(AuthenticationConstants.SERVICE_URL_CLAIM, TestBotEndpoint);

                        return new ClaimsIdentity("anonymous", claims);
                    }
                }
            );
            return auth;
        }
    }

    private static class TestSkillConversationIdFactory extends SkillConversationIdFactoryBase {
        private final ConcurrentHashMap<String, String> conversationRefs = new ConcurrentHashMap<>();

        public CompletableFuture<String> createSkillConversationId(SkillConversationIdFactoryOptions options) {
            SkillConversationReference skillConversationReference = new SkillConversationReference();
            skillConversationReference.setConversationReference(options.getActivity().getConversationReference());
            skillConversationReference.setOAuthScope(options.getFromBotOAuthScope());

            String key =
                String.format(
                    "%1$s-%2$s-%3$s-%4$s-skillconvo",
                    options.getFromBotId(),
                    options.getBotFrameworkSkill().getAppId(),
                    skillConversationReference.getConversationReference().getConversation().getId(),
                    skillConversationReference.getConversationReference().getChannelId());

            JacksonAdapter jacksonAdapter = new JacksonAdapter();
            try {
                conversationRefs.putIfAbsent(key, jacksonAdapter.serialize(skillConversationReference));
            }
            catch (IOException e) {
            }
            return CompletableFuture.completedFuture(key);
        }

        @Override
        public CompletableFuture<SkillConversationReference> getSkillConversationReference(String skillConversationId) {
            SkillConversationReference conversationReference = null;
            try {
                JacksonAdapter jacksonAdapter = new JacksonAdapter();
                conversationReference = jacksonAdapter.deserialize(
                    conversationRefs.get(skillConversationId),
                    SkillConversationReference.class);
            }
            catch (IOException e) {
            }

            return CompletableFuture.completedFuture(conversationReference);
        }

        @Override
        public CompletableFuture<Void> deleteConversationReference(String skillConversationId) {
            conversationRefs.remove(skillConversationId);
            return CompletableFuture.completedFuture(null);
        }
    }
}
