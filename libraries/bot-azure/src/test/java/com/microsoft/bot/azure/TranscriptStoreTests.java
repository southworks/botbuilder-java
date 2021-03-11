// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.microsoft.bot.azure.blobs.BlobsTranscriptStore;
import com.microsoft.bot.builder.PagedResult;
import com.microsoft.bot.builder.TranscriptInfo;
import com.microsoft.bot.builder.TranscriptLoggerMiddleware;
import com.microsoft.bot.builder.TranscriptStore;
import com.microsoft.bot.builder.adapters.TestAdapter;
import com.microsoft.bot.builder.adapters.TestFlow;
import com.microsoft.bot.restclient.serializer.JacksonAdapter;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ConversationAccount;
import com.microsoft.bot.schema.ConversationReference;
import com.microsoft.bot.schema.ResourceResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * These tests require Azure Storage Emulator v5.7
 * The emulator must be installed at this path C:\Program Files (x86)\Microsoft SDKs\Azure\Storage Emulator\AzureStorageEmulator.exe
 * More info: https://docs.microsoft.com/azure/storage/common/storage-use-emulator
 */
public class TranscriptStoreTests {

    @Rule
    private static final TestName TEST_NAME = new TestName();

    protected String blobStorageEmulatorConnectionString =
        "AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;DefaultEndpointsProtocol=http;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;";

    protected static final String[] LONG_ID = {
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq1234567890Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq098765432112345678900987654321Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq123456789009876543211234567890Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq09876543211234567890098765432112345678900987654321"
    };

    private String channelId = "test";

    private static final String[] CONVERSATION_IDS = {
        "qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm", "123", "456", "789",
        "ZAQ", "XSW", "CDE", "VFR", "BGT", "NHY", "NHY", "098", "765", "432",
        "zxc", "vbn", "mlk", "jhy", "yui", "kly", "asd", "asw", "aaa", "zzz",
    };

    private static final String[] CONVERSATION_SPECIAL_IDS = { "asd !&/#.'+:?\"", "ASD@123<>|}{][", "$%^;\\*()_" };

    protected String containerName = String.format("blobstranscript%s", TEST_NAME.getMethodName());

    protected TranscriptStore transcriptStore = new BlobsTranscriptStore(blobStorageEmulatorConnectionString, containerName);

    private static BlobContainerClient testBlobClient;

    protected static Boolean EMULATOR_IS_RUNNING = false;

    @BeforeClass
    public static void allTestsInit() throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec
            ("cmd /C \"" + System.getenv("Program Files (x86)") + "\\Microsoft SDKs\\Azure\\Storage Emulator\\AzureStorageEmulator.exe");
        int result = p.waitFor();
        // status = 0: the service was started.
        // status = -5: the service is already started. Only one instance of the application
        // can be run at the same time.
        EMULATOR_IS_RUNNING = result == 0 || result == -5;
    }

    @Before
    public void testInit() {
        if (EMULATOR_IS_RUNNING) {
            testBlobClient = new BlobContainerClientBuilder()
                .connectionString(blobStorageEmulatorConnectionString)
                .containerName(containerName)
                .buildClient();
            if (!testBlobClient.exists()) {
                testBlobClient.create();
            }
        }
    }

    @After
    public void testCleanup() {
        if (testBlobClient.exists()) {
            testBlobClient.delete();
        }
    }

    // These tests require Azure Storage Emulator v5.7
    @Test
    public void blobTranscriptParamTest() {
        if(EMULATOR_IS_RUNNING) {
            Assert.assertThrows(IllegalArgumentException.class, () -> new BlobsTranscriptStore(null, containerName));
            Assert.assertThrows(IllegalArgumentException.class, () -> new BlobsTranscriptStore(blobStorageEmulatorConnectionString, null));
            Assert.assertThrows(IllegalArgumentException.class, () -> new BlobsTranscriptStore(new String(), containerName));
            Assert.assertThrows(IllegalArgumentException.class, () -> new BlobsTranscriptStore(blobStorageEmulatorConnectionString, new String()));
        }
    }

    @Test
    public void transcriptsEmptyTest() {
        if(EMULATOR_IS_RUNNING) {
            String unusedChannelId = UUID.randomUUID().toString();
            PagedResult<TranscriptInfo> transcripts = transcriptStore.listTranscripts(unusedChannelId).join();
            Assert.assertEquals(0, transcripts.getItems().size());
        }
    }

    @Test
    public void activityEmptyTest() {
        if (EMULATOR_IS_RUNNING) {
            for(String convoId: CONVERSATION_SPECIAL_IDS) {
                PagedResult<Activity> activities = transcriptStore.getTranscriptActivities(channelId, convoId).join();
                Assert.assertEquals(0, activities.getItems().size());
            }
        }
    }

    @Test
    public void activityAddTest() {
        if(EMULATOR_IS_RUNNING) {
            Activity[] loggedActivities = new Activity[5];
            List<Activity> activities = new ArrayList<Activity>();
            for (int i = 0; i < 5; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, CONVERSATION_IDS);
                transcriptStore.logActivity(a).join();
                activities.add(a);
                loggedActivities[i] = transcriptStore.getTranscriptActivities(channelId, CONVERSATION_IDS[i])
                    .join().getItems().get(0);
            }

            Assert.assertEquals(5, loggedActivities.length);
        }
    }

    @Test
    public void transcriptRemoveTest() {
        if (EMULATOR_IS_RUNNING) {
            for (int i = 0; i < 5; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, CONVERSATION_IDS);
                transcriptStore.logActivity(a).join();
                transcriptStore.deleteTranscript(a.getChannelId(), a.getConversation().getId()).join();

                PagedResult<Activity> loggedActivities = transcriptStore
                    .getTranscriptActivities(channelId, CONVERSATION_IDS[i]).join();

                Assert.assertEquals(0, loggedActivities.getItems().size());
            }
        }
    }

    @Test
    public void activityAddSpecialCharsTest() {
        if (EMULATOR_IS_RUNNING) {
            Activity[] loggedActivities = new Activity[CONVERSATION_SPECIAL_IDS.length];
            List<Activity> activities = new ArrayList<Activity>();
            for (int i = 0; i < CONVERSATION_SPECIAL_IDS.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, CONVERSATION_SPECIAL_IDS);
                transcriptStore.logActivity(a).join();
                activities.add(a);
                loggedActivities[i] = transcriptStore.getTranscriptActivities(channelId, CONVERSATION_SPECIAL_IDS[i])
                   .join().getItems().get(0);
            }

            Assert.assertEquals(activities.size(), loggedActivities.length);
        }
    }

    @Test
    public void transcriptRemoveSpecialCharsTest() {
        if (EMULATOR_IS_RUNNING) {
            for (int i = 0; i < CONVERSATION_SPECIAL_IDS.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, CONVERSATION_SPECIAL_IDS);
                transcriptStore.deleteTranscript(a.getChannelId(), a.getConversation().getId()).join();

                PagedResult<Activity> loggedActivities = transcriptStore.
                    getTranscriptActivities(channelId, CONVERSATION_SPECIAL_IDS[i]).join();
                Assert.assertEquals(0, loggedActivities.getItems().size());
            }
        }
    }

    @Test
    public void activityAddPagedResultTest() {
        if (EMULATOR_IS_RUNNING) {
            String cleanChannel = UUID.randomUUID().toString();

            List<Activity> activities = new ArrayList<Activity>();

            for (int i = 0; i < CONVERSATION_IDS.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, CONVERSATION_IDS);
                a.setChannelId(cleanChannel);

                transcriptStore.logActivity(a).join();
                activities.add(a);
            }

            PagedResult<Activity> loggedPagedResult = transcriptStore.getTranscriptActivities(channelId, CONVERSATION_IDS[0]).join();
            String ct = loggedPagedResult.getContinuationToken();
            Assert.assertEquals(20, loggedPagedResult.getItems().size());
            Assert.assertNotNull(ct);
            Assert.assertTrue(loggedPagedResult.getContinuationToken().length() > 0);
            loggedPagedResult = transcriptStore.getTranscriptActivities(cleanChannel, CONVERSATION_IDS[0], ct).join();
            ct = loggedPagedResult.getContinuationToken();
            Assert.assertEquals(10, loggedPagedResult.getItems().size());
            Assert.assertNull(ct);
        }
    }

    @Test
    public void transcriptRemovePagedTest() {
        if (EMULATOR_IS_RUNNING) {
            int i;
            for (i = 0; i < CONVERSATION_SPECIAL_IDS.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i ,i , CONVERSATION_IDS);
                transcriptStore.deleteTranscript(a.getChannelId(), a.getConversation().getId()).join();
            }

            PagedResult<Activity> loggedActivities = transcriptStore.getTranscriptActivities(channelId, CONVERSATION_IDS[i]).join();
            Assert.assertEquals(0, loggedActivities.getItems().size());
        }
    }

    @Test
    public void nullParameterTests() {
        if (EMULATOR_IS_RUNNING) {
            TranscriptStore store = transcriptStore;

            Assert.assertThrows(CompletionException.class, () -> store.logActivity(null));
            Assert.assertThrows(CompletionException.class,
                () -> store.getTranscriptActivities(null, CONVERSATION_IDS[0]));
            Assert.assertThrows(CompletionException.class, () -> store.getTranscriptActivities(channelId, null));
        }
    }

    @Test
    public void logActivities() {
        if (EMULATOR_IS_RUNNING) {
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation)
                .use(new TranscriptLoggerMiddleware(transcriptStore));
            new TestFlow(adapter, turnContext -> {
                Activity typingActivity = new Activity() {{
                    setType(ActivityTypes.TYPING);
                    setRelatesTo(turnContext.getActivity().getRelatesTo());
                }};
                turnContext.sendActivity(typingActivity).join();
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    // Empty error
                }
                turnContext.sendActivity(String.format("echo:%s", turnContext.getActivity().getText())).join();
                return CompletableFuture.completedFuture(null);
            })
                .send("foo")
                    .assertReply(activity ->
                        Assert.assertTrue(activity.isType(ActivityTypes.TYPING))
                    )
                    .assertReply("echo:foo")
                .send("bar")
                    .assertReply(activity ->
                        Assert.assertTrue(activity.isType(ActivityTypes.TYPING))
                    )
                    .assertReply("echo:bar")
                .startTest().join();

            PagedResult<Activity> pagedResult = null;
            try {
                pagedResult = this.getPagedResult(conversation, 6, null).join();
            } catch (TimeoutException ex) {
                Assert.fail();
            }
            Assert.assertEquals(6, pagedResult.getItems().size());
            Assert.assertTrue(pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            Assert.assertNotNull(pagedResult.getItems().get(1));
            Assert.assertTrue(pagedResult.getItems().get(1).isType(ActivityTypes.TYPING));
            Assert.assertTrue(pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("echo:foo", pagedResult.getItems().get(2).getText());
            Assert.assertTrue(pagedResult.getItems().get(3).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("bar", pagedResult.getItems().get(3).getText());
            Assert.assertNotNull(pagedResult.getItems().get(4));
            Assert.assertTrue(pagedResult.getItems().get(4).isType(ActivityTypes.TYPING));
            Assert.assertTrue(pagedResult.getItems().get(5).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("echo:bar", pagedResult.getItems().get(5).getText());
            for (Activity activity: pagedResult.getItems()) {
                Assert.assertTrue(!StringUtils.isBlank(activity.getId()));
                Assert.assertTrue(activity.getTimestamp().isAfter(OffsetDateTime.now()));
            }
        }
    }

    @Test
    public void logUpdateActivities() {
        if (EMULATOR_IS_RUNNING) {
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation)
                .use(new TranscriptLoggerMiddleware(transcriptStore));
            new TestFlow(adapter, turnContext -> {
                Activity activityToUpdate = new Activity(ActivityTypes.MESSAGE);
                if(turnContext.getActivity().getText().equals("update")) {
                    activityToUpdate.setText("new response");
                    turnContext.updateActivity(activityToUpdate).join();
                } else {
                    Activity activity = turnContext.getActivity().createReply("response");
                    ResourceResponse response = turnContext.sendActivity(activity).join();
                    activity.setId(response.getId());

                    JacksonAdapter jacksonAdapter = new JacksonAdapter();
                    try {
                        // clone the activity, so we can use it to do an update
                        activityToUpdate = jacksonAdapter.deserialize(jacksonAdapter.serialize(activity), Activity.class);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                return CompletableFuture.completedFuture(null);
            }).send("foo")
              .send("update")
                .assertReply("new response")
              .startTest().join();

            PagedResult<Activity> pagedResult = null;
            try {
                pagedResult = this.getPagedResult(conversation, 3, null).join();
            } catch (TimeoutException ex) {
                Assert.fail();
            }

            Assert.assertEquals(3, pagedResult.getItems().size());
            Assert.assertTrue(pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            Assert.assertTrue(pagedResult.getItems().get(1).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("new response", pagedResult.getItems().get(1).getText());
            Assert.assertTrue(pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("update", pagedResult.getItems().get(2).getText());
        }
    }

    @Test
    public void testDateLogUpdateActivities() {
        if (EMULATOR_IS_RUNNING) {
            OffsetDateTime dateTimeStartOffset1 = OffsetDateTime.now();
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation)
                .use(new TranscriptLoggerMiddleware(transcriptStore));
            new TestFlow(adapter, turnContext -> {
                Activity activityToUpdate = new Activity(ActivityTypes.MESSAGE);
                if (turnContext.getActivity().getText().equals("update")) {
                    activityToUpdate.setText("new response");
                    turnContext.updateActivity(activityToUpdate).join();
                } else {
                    Activity activity = turnContext.getActivity().createReply("response");

                    ResourceResponse response = turnContext.sendActivity(activity).join();
                    activity.setId(response.getId());

                    JacksonAdapter jacksonAdapter = new JacksonAdapter();
                    try {
                        // clone the activity, so we can use it to do an update
                        activityToUpdate = jacksonAdapter.deserialize(jacksonAdapter.serialize(activity), Activity.class);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                return CompletableFuture.completedFuture(null);
            }).send("foo")
              .send("update")
                    .assertReply("new response")
              .startTest().join();

            try {
                TimeUnit.MILLISECONDS.sleep(5000);
            } catch (InterruptedException e) {
                // Empty error
            }

            // Perform some queries
            PagedResult<Activity> pagedResult = transcriptStore.getTranscriptActivities(
                conversation.getChannelId(),
                conversation.getConversation().getId(),
                null,
                dateTimeStartOffset1).join();
            Assert.assertEquals(3, pagedResult.getItems().size());
            Assert.assertTrue(pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            Assert.assertTrue(pagedResult.getItems().get(1).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("new response", pagedResult.getItems().get(1).getText());
            Assert.assertTrue(pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("update", pagedResult.getItems().get(2).getText());

            // Perform some queries
            pagedResult = transcriptStore.getTranscriptActivities(
                conversation.getChannelId(),
                conversation.getConversation().getId(),
                null,
                OffsetDateTime.MIN).join();
            Assert.assertEquals(3, pagedResult.getItems().size());
            Assert.assertTrue(pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            Assert.assertTrue(pagedResult.getItems().get(1).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("new response", pagedResult.getItems().get(1).getText());
            Assert.assertTrue(pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("update", pagedResult.getItems().get(2).getText());

            // Perform some queries
            pagedResult = transcriptStore.getTranscriptActivities(
                conversation.getChannelId(),
                conversation.getConversation().getId(),
                null,
                OffsetDateTime.MAX).join();
            Assert.assertEquals(0, pagedResult.getItems().size());
        }
    }

    @Test
    public void logDeleteActivities() {
        if (EMULATOR_IS_RUNNING) {
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation)
                .use(new TranscriptLoggerMiddleware(transcriptStore));
            new TestFlow(adapter, turnContext -> {
                String activityId = null;
                if (turnContext.getActivity().getText().equals("deleteIt")) {
                    turnContext.deleteActivity(activityId).join();
                } else {
                    Activity activity = turnContext.getActivity().createReply("response");
                    ResourceResponse response = turnContext.sendActivity(activity).join();
                    activityId = response.getId();
                }
                return CompletableFuture.completedFuture(null);
            }).send("foo")
                .assertReply("response")
              .send("deleteIt")
              .startTest().join();

            PagedResult<Activity> pagedResult = null;
            try {
                pagedResult = this.getPagedResult(conversation, 3, null).join();
            } catch (TimeoutException ex) {
                Assert.fail();
            }

            Assert.assertEquals(3, pagedResult.getItems().size());
            Assert.assertTrue(pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            Assert.assertNotNull(pagedResult.getItems().get(1));
            Assert.assertTrue(pagedResult.getItems().get(1).isType(ActivityTypes.MESSAGE_DELETE));
            Assert.assertTrue(pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE));
            Assert.assertEquals("deleteIt", pagedResult.getItems().get(2).getText());
        }
    }

    protected static Activity createActivity(Integer i, Integer j, String[] CONVERSATION_IDS) {
        return TranscriptStoreTests.createActivity(j, CONVERSATION_IDS[i]);
    }

    private static Activity createActivity(Integer j, String conversationId) {
        ConversationAccount conversationAccount = new ConversationAccount() {{
            setId(conversationId);
        }};
        return new Activity() {{
            setId(StringUtils.leftPad(String.valueOf(j + 1), 2, "0"));
            setChannelId("test");
            setText("test");
            setType(ActivityTypes.MESSAGE);
            setConversation(conversationAccount);
            setTimestamp(OffsetDateTime.now());
            setFrom(new ChannelAccount("testUser"));
            setRecipient(new ChannelAccount("testBot"));
        }};
    }

    /**
     * There are some async oddities within TranscriptLoggerMiddleware that make it difficult to set a short delay when
     * running this tests that ensures
     * the TestFlow completes while also logging transcripts. Some tests will not pass without longer delays,
     * but this method minimizes the delay required.
     * @param conversation ConversationReference to pass to GetTranscriptActivitiesAsync()
     *                     that contains ChannelId and Conversation.Id.
     * @param expectedLength Expected length of pagedResult array.
     * @param maxTimeout Maximum time to wait to retrieve pagedResult.
     * @return PagedResult.
     * @throws TimeoutException
     */
    private CompletableFuture<PagedResult<Activity>> getPagedResult(ConversationReference conversation,
                                                                    Integer expectedLength, Integer maxTimeout) throws TimeoutException {
        if (maxTimeout == null) {
            maxTimeout = 5000;
        }

        PagedResult<Activity> pagedResult = null;
        for (int timeout = 0; timeout < maxTimeout; timeout += 500) {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                // Empty error
            }
            try {
                pagedResult = transcriptStore
                    .getTranscriptActivities(conversation.getChannelId(), conversation.getConversation().getId()).join();
                if (pagedResult.getItems().size() >= expectedLength) {
                    break;
                }
            } catch (NoSuchElementException ex) {

            } catch (NullPointerException e) {

            }
        }

        if(pagedResult == null) {
            throw new TimeoutException("Unable to retrieve pagedResult in time");
        }

        return CompletableFuture.completedFuture(pagedResult);
    }
}
