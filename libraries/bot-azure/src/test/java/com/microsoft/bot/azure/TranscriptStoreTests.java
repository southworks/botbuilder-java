// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.microsoft.bot.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Since the class AzureBlobTranscriptStore.cs is deprecated we decided to merge the BlobsTranscriptStoreTests
 * and the TranscriptStoreBaseTests in only one class.
 */
//TODO this class need the BlobsTranscriptStore class
public class TranscriptStoreTests {

    @Rule
    private static final TestName testName = new TestName();

    private static String channelId = "test";

    private static final String[] conversationIds = {
        "qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm", "123", "456", "789",
        "ZAQ", "XSW", "CDE", "VFR", "BGT", "NHY", "NHY", "098", "765", "432",
        "zxc", "vbn", "mlk", "jhy", "yui", "kly", "asd", "asw", "aaa", "zzz",
    };

    private static final String[] conversationSpecialIds = { "asd !&/#.'+:?\"", "ASD@123<>|}{][", "$%^;\\*()_" };

    private static BlobContainerClient testBlobClient;

    protected static String containerName = String.format("blobstranscript%s", testName.getMethodName());

    protected static String blobStorageEmulatorConnectionString =
        "AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;DefaultEndpointsProtocol=http;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;";

    protected TranscriptStore transcriptStore = new BlobsTranscriptStore(blobStorageEmulatorConnectionString, containerName);

    protected static Boolean EMULATOR_IS_RUNNING = false;

    protected static final String[] longId = {
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq1234567890Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq098765432112345678900987654321Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq123456789009876543211234567890Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq09876543211234567890098765432112345678900987654321"
    };

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
    public void longIdAddTest() {
        if(EMULATOR_IS_RUNNING) {
            try {
                Activity a = TranscriptStoreTests.createActivity(0 ,0 , longId);

                transcriptStore.logActivity(a).join();
                Assert.fail("Should have thrown an error");
            } catch (Exception ex) {
                // Verify if Java Azure Storage Blobs 12.10.0 has the same behavior
                // From C#: Unfortunately, Azure.Storage.Blobs v12.4.4 currently throws this XmlException for long keys :(
                if (StringUtils.equals(ex.getMessage(), "'\\\"' is an unexpected token. Expecting whitespace. Line 1, position 50.")) {
                    return;
                }
            }

            Assert.fail("Should have thrown an error");
        }
    }

    @Test
    public void blobTranscriptParamTest() {
        if(EMULATOR_IS_RUNNING) {
            try {
                new BlobsTranscriptStore(null, containerName);
                Assert.fail("should have thrown for null connection string");
            } catch (Exception ex) {
                // all good
            }

            try {
                new BlobsTranscriptStore(blobStorageEmulatorConnectionString, null);
                Assert.fail("should have thrown for null containerName");
            } catch (Exception ex) {
                // all good
            }

            try {
                new BlobsTranscriptStore(new String(), containerName);
                Assert.fail("should have thrown for empty connection string");
            } catch (Exception ex) {
                // all good
            }

            try {
                new BlobsTranscriptStore(blobStorageEmulatorConnectionString, new String());
                Assert.fail("should have thrown for empty container name");
            } catch (Exception ex) {
                // all good
            }
        }
    }

    @Test
    public void transcriptEmptyTest() {
        if(EMULATOR_IS_RUNNING) {
            String unusedChannelId = UUID.randomUUID().toString();
            PagedResult<TranscriptInfo> transcripts = transcriptStore.listTranscripts(unusedChannelId).join();
            Assert.assertEquals(transcripts.getItems().size(), 0);
        }
    }

    @Test
    public void activityAddTest() {
        if(EMULATOR_IS_RUNNING) {
            Activity[] loggedActivities = new Activity[5];
            List<Activity> activities = new ArrayList<Activity>();
            for (int i = 0; i < 5; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, conversationIds);
                transcriptStore.logActivity(a).join();
                activities.add(a);
                loggedActivities[i] = transcriptStore.getTranscriptActivities(channelId, conversationIds[i])
                    .join().getItems().get(0);
            }

            Assert.assertEquals(5, loggedActivities.length);
        }
    }

    @Test
    public void transcriptRemoveTest() {
        if (EMULATOR_IS_RUNNING) {
            for (int i = 0; i < 5; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, conversationIds);
                transcriptStore.logActivity(a).join();
                transcriptStore.deleteTranscript(a.getChannelId(), a.getConversation().getId()).join();

                PagedResult<Activity> loggedActivities = transcriptStore
                    .getTranscriptActivities(channelId, conversationIds[i]).join();

                Assert.assertEquals(loggedActivities.getItems().size(), 0);
            }
        }
    }

    @Test
    public void activityAddSpecialCharsTest() {
        if (EMULATOR_IS_RUNNING) {
            Activity[] loggedActivities = new Activity[conversationSpecialIds.length];
            List<Activity> activities = new ArrayList<Activity>();
            for (int i = 0; i < conversationSpecialIds.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, conversationIds);
                transcriptStore.logActivity(a).join();
                activities.add(a);
                loggedActivities[i] = transcriptStore.getTranscriptActivities(channelId, conversationSpecialIds[i])
                   .join().getItems().get(0);
            }

            Assert.assertEquals(activities.size(), loggedActivities.length);
        }
    }

    @Test
    public void transcriptRemoveSpecialCharsTest() {
        if (EMULATOR_IS_RUNNING) {
            for (int i = 0; i < conversationSpecialIds.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, conversationIds);
                transcriptStore.deleteTranscript(a.getChannelId(), a.getConversation().getId());

                PagedResult<Activity> loggedActivities = transcriptStore.
                    getTranscriptActivities(channelId, conversationSpecialIds[i]).join();
                Assert.assertEquals(loggedActivities.getItems().size(), 0);
            }
        }
    }

    @Test
    public void activityAddPagedResultTest() {
        if (EMULATOR_IS_RUNNING) {
            String cleanChanel = UUID.randomUUID().toString();

            PagedResult<Activity> loggedPagedResult = new PagedResult<Activity>();
            List<Activity> activities = new ArrayList<Activity>();

            for (int i = 0; i < conversationIds.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i, conversationIds);
                a.setChannelId(cleanChanel);

                transcriptStore.logActivity(a).join();
                activities.add(a);
            }

            loggedPagedResult = transcriptStore.getTranscriptActivities(channelId, conversationIds[0]).join();

            String ct = loggedPagedResult.getContinuationToken();
            Assert.assertEquals(20, loggedPagedResult.getItems().size());
            Assert.assertTrue(ct != null);
            Assert.assertTrue(loggedPagedResult.getContinuationToken().length() > 0);
            loggedPagedResult = transcriptStore.getTranscriptActivities(cleanChanel, conversationIds[0], ct).join();
            ct = loggedPagedResult.getContinuationToken();
            Assert.assertEquals(10, loggedPagedResult.getItems().size());
            Assert.assertTrue(ct != null);
        }
    }

    @Test
    public void transcriptRemovePagedTest() {
        if (EMULATOR_IS_RUNNING) {
            PagedResult<Activity> loggedActivities = new PagedResult<Activity>();
            int i;
            for (i = 0; i < conversationSpecialIds.length; i++) {
                Activity a = TranscriptStoreTests.createActivity(i, i , conversationIds);
                transcriptStore.deleteTranscript(a.getChannelId(), a.getConversation().getId()).join();
            }

            loggedActivities = transcriptStore.getTranscriptActivities(channelId, conversationIds[i]).join();
            Assert.assertTrue(loggedActivities.getItems().size() == 0);
        }
    }

    @Test
    public void nullParameterTest() {
        if (EMULATOR_IS_RUNNING) {
            TranscriptStore store = transcriptStore;

            try {
                store.logActivity(null);
                Assert.fail("should have thrown for null activity in logActivity method");
            } catch (Exception ex) {
                // all good
            }

            try {
                store.getTranscriptActivities(null, conversationIds[0]);
                Assert.fail("should have thrown for null channelId in getTranscriptActivities method");
            } catch (Exception ex) {
                // all good
            }

            try {
                store.getTranscriptActivities(channelId, null);
                Assert.fail("should have thrown for null conversationId in getTranscriptActivities method");
            } catch (Exception ex) {
                // all good
            }
        }
    }

    @Test
    public void logActivities() {
        if (EMULATOR_IS_RUNNING) {
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation).use(new TranscriptLoggerMiddleware(transcriptStore));
            new TestFlow(adapter, turnContext -> {
                Activity typingActivity = new Activity() {
                    {
                        setType(ActivityTypes.TYPING);
                        setRelatesTo(turnContext.getActivity().getRelatesTo());
                    }
                };
                turnContext.sendActivity(typingActivity).join();
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    // Empty error
                }
                turnContext.sendActivity(String.format("echo:%s", turnContext.getActivity().getText())).join();
                return CompletableFuture.completedFuture(null);
            })
                .send("foo").assertReply(activity -> {
                    Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
            }).assertReply("echo:foo")
                .send("bar").assertReply(activity -> {
                Assert.assertTrue(activity.isType(ActivityTypes.TYPING));
            }).assertReply("echo:bar")
                .startTest().join();

            PagedResult<Activity> pagedResult = null;
            try {
                pagedResult = this.getPagedResult(conversation, 6, null).join();
            } catch (TimeoutException ex) {
                Assert.fail();
            }
            Assert.assertEquals(6, pagedResult.getItems().size());
            if(pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            } else {
                Assert.fail();
            }

            Assert.assertTrue(pagedResult.getItems().get(1) != null);
            if(!pagedResult.getItems().get(1).isType(ActivityTypes.TYPING)) {
                Assert.fail();
            }

            if(pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("echo:foo", pagedResult.getItems().get(2).getText());
            } else {
                Assert.fail();
            }

            if(pagedResult.getItems().get(3).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("bar", pagedResult.getItems().get(3).getText());
            } else {
                Assert.fail();
            }

            Assert.assertTrue(pagedResult.getItems().get(4) != null);
            if(!pagedResult.getItems().get(4).isType(ActivityTypes.TYPING)) {
                Assert.fail();
            }

            if(pagedResult.getItems().get(5).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("echo:bar", pagedResult.getItems().get(3).getText());
            } else {
                Assert.fail();
            }

            for (Activity activity: pagedResult.getItems()) {
                Assert.assertTrue(!StringUtils.isBlank(activity.getId()));
                //this is the default DateTimeOffset in C#
                OffsetDateTime defaultDate = OffsetDateTime.of(0001,1,1,12,0,0,0,OffsetDateTime.now().getOffset());
                Assert.assertTrue(activity.getTimestamp().isAfter(defaultDate));
            }
        }
    }

    @Test
    public void logUpdateActivities() {
        if (EMULATOR_IS_RUNNING) {
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation).use(new TranscriptLoggerMiddleware(transcriptStore));
            final Activity[] activityToUpdate = {null};
            new TestFlow(adapter, turnContext -> {
                activityToUpdate[0] = new Activity(ActivityTypes.MESSAGE);
                if(turnContext.getActivity().getText().equals("update")) {
                    activityToUpdate[0].setText("new response");
                    turnContext.updateActivity(activityToUpdate[0]).join();
                } else {
                    Activity activity = turnContext.getActivity().createReply("response");
                    ResourceResponse response = turnContext.sendActivity(activity).join();
                    activity.setId(response.getId());

                    // clone the activity, so we can use it to do an update
                    JacksonAdapter jacksonAdapter = new JacksonAdapter();
                    try {
                        activityToUpdate[0] = jacksonAdapter.deserialize(jacksonAdapter.serialize(activity), Activity.class);
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                }
                return CompletableFuture.completedFuture(null);
            }).send("foo")
                .send("update")
                .assertReply("new response")
                .startTest().join();
        }
    }

    @Test
    public void testDateLogUpdateActivities() {
        if (EMULATOR_IS_RUNNING) {
            OffsetDateTime dateTimeStartOffset1 = OffsetDateTime.now();
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation).use(new TranscriptLoggerMiddleware(transcriptStore));
            final Activity[] activityToUpdate = {null};
            new TestFlow(adapter, turnContext -> {
                if (turnContext.getActivity().getText().equals("update")) {
                    activityToUpdate[0].setText("new response");
                    turnContext.updateActivity(activityToUpdate[0]).join();
                } else {
                    Activity activity = turnContext.getActivity().createReply("response");
                    ResourceResponse response = turnContext.sendActivity(activity).join();
                    activity.setId(response.getId());

                    // clone the activity, so we can use it to do an update
                    JacksonAdapter jacksonAdapter = new JacksonAdapter();
                    try {
                        activityToUpdate[0] = jacksonAdapter.deserialize(jacksonAdapter.serialize(activity), Activity.class);
                    } catch (IOException exception) {
                        exception.printStackTrace();
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
            PagedResult<Activity> pagedResult = transcriptStore.getTranscriptActivities(conversation.getChannelId(),
                conversation.getConversation().getId(), null, dateTimeStartOffset1).join();
            Assert.assertEquals(3, pagedResult.getItems().size());
            if (pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
                Assert.assertEquals("new response", pagedResult.getItems().get(1).getText());
                Assert.assertEquals("update", pagedResult.getItems().get(2).getText());
            } else {
                Assert.fail();
            }

            // Perform some queries
            //The MinValue in C# is 1/1/0001 12:00:00 AM
            OffsetDateTime MinDateTime = OffsetDateTime.of(0001,1,1,12,0,0,0,OffsetDateTime.now().getOffset());
            pagedResult = transcriptStore.getTranscriptActivities(conversation.getChannelId(),
                conversation.getConversation().getId(), null, MinDateTime).join();
            Assert.assertEquals(3, pagedResult.getItems().size());
            if (pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
                Assert.assertEquals("new response", pagedResult.getItems().get(1).getText());
                Assert.assertEquals("update", pagedResult.getItems().get(2).getText());
            } else {
                Assert.fail();
            }

            // Perform some queries
            //The MaxValue in C# is 12/31/9999 11:59:59 PM +00:00
            OffsetDateTime MaxDateTime = OffsetDateTime.of(9999,12,31,12,59,59,0,OffsetDateTime.now().getOffset());
            Assert.assertTrue(pagedResult.getItems().isEmpty());
        }
    }

    @Test
    public void logDeleteActivities() {
        if (EMULATOR_IS_RUNNING) {
            ConversationReference conversation = TestAdapter
                .createConversationReference(UUID.randomUUID().toString(), "User1", "Bot");
            TestAdapter adapter = new TestAdapter(conversation).use(new TranscriptLoggerMiddleware(transcriptStore));
            final String[] activityId = {null};
            new TestFlow(adapter, turnContext -> {
                if (turnContext.getActivity().getText().equals("deleteIt")) {
                    turnContext.deleteActivity(activityId[0]);
                } else {
                    Activity activity = turnContext.getActivity().createReply("response");
                    ResourceResponse response = turnContext.sendActivity(activity).join();
                    activityId[0] = response.getId();
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
            if (pagedResult.getItems().get(0).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("foo", pagedResult.getItems().get(0).getText());
            } else {
                Assert.fail();
            }

            Assert.assertTrue(pagedResult.getItems().get(1) != null);
            Assert.assertTrue(pagedResult.getItems().get(1).isType(ActivityTypes.MESSAGE_DELETE));

            if (pagedResult.getItems().get(2).isType(ActivityTypes.MESSAGE)) {
                Assert.assertEquals("deleteIt", pagedResult.getItems().get(2).getText());
            } else {
                Assert.fail();
            }
        }
    }

    protected static Activity createActivity(int i, int j, String[] conversationIds) {
        return TranscriptStoreTests.createActivity(j, conversationIds[i]);
    }

    private static Activity createActivity(int j, String conversationId) {
        ConversationAccount conversationAccount = new ConversationAccount() {
            {
                setId(conversationId);
            }
        };
        return new Activity() {
            {
                setId(StringUtils.leftPad(String.valueOf(j + 1), 2, "0"));
                setChannelId("test");
                setText("test");
                setType(ActivityTypes.MESSAGE);
                setConversation(conversationAccount);
                setTimestamp(OffsetDateTime.now());
                setFrom(new ChannelAccount("testUser"));
                setRecipient(new ChannelAccount("testBot"));
            }
        };
    }

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
                if (pagedResult.getItems().size() > expectedLength) {
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
