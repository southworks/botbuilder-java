// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.microsoft.bot.ai.qna.models.FeedbackRecord;
import com.microsoft.bot.ai.qna.models.FeedbackRecords;
import com.microsoft.bot.ai.qna.models.Metadata;
import com.microsoft.bot.ai.qna.models.QnAMakerTraceInfo;
import com.microsoft.bot.ai.qna.models.QueryResult;
import com.microsoft.bot.builder.MemoryTranscriptStore;
import com.microsoft.bot.builder.TranscriptLoggerMiddleware;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.TurnContextImpl;
import com.microsoft.bot.builder.adapters.TestAdapter;
import com.microsoft.bot.builder.adapters.TestFlow;

import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ConversationAccount;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class QnAMakerTests {
    private final String knowledgeBaseId = "dummy-id";
    private final String endpointKey = "dummy-key";
    private final String hostname = "https://dummy-hostname.azurewebsites.net/qnamaker";

    private String getRequestUrl() {
        return String.format("%1$s/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivity() {
        // Mock Qna
        QnAMaker qna = this.qnaReturnsAnswer();

        // Invoke flow which uses mock
        MemoryTranscriptStore transcriptStore = new MemoryTranscriptStore();
        TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity", null, null))
                        .use(new TranscriptLoggerMiddleware(transcriptStore));
        String conversationId = null;
        new TestFlow(adapter, (turnContext) -> {
            // Simulate Qna Lookup
            if (turnContext.getActivity().getText().compareTo("how do I clean the stove?") == 0) {
                return qna.getAnswers(turnContext, null).thenApply(results -> {
                    Assert.assertNotNull(results);
                    Assert.assertTrue(results.length == 1);
                    Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                            results[0].getAnswer());
                    return null;
                }).thenCompose(task -> {
                    conversationId = turnContext.getActivity().getConversation().getId();
                    Activity typingActivity = new Activity() {
                        {
                            setType(ActivityTypes.TYPING);
                            setRelatesTo(turnContext.getActivity().getRelatesTo());
                        }
                    };
                    return turnContext.sendActivity(typingActivity);
                }).thenCompose(task -> {
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        // no handle exception
                    }
                    return turnContext.sendActivity(String.format("echo: %s", turnContext.getActivity().getText()));
                });
            }
        }).send("how do I clean the stove?").assertReply(activity -> {
            Assert.assertEquals(activity.getType(), ActivityTypes.TYPING);

        }).assertReply("echo:how do I clean the stove?").send("bar")
                .assertReply(activity -> Assert.assertEquals(activity.getType(), ActivityTypes.TYPING))
                .assertReply("echo:bar").startTest().join();

        // Validate Trace Activity created
        transcriptStore.getTranscriptActivities("test", conversationId).thenApply(pagedResult -> {
            Assert.assertEquals(7, pagedResult.getItems().size());
            Assert.assertEquals("how do I clean the stove?", pagedResult.getItems().get(0).getText());
            Assert.assertEquals(0, pagedResult.getItems().get(1).getType().compareTo(ActivityTypes.TRACE));
            QnAMakerTraceInfo traceInfo = (QnAMakerTraceInfo) pagedResult.getItems().get(1).getValue();
            Assert.assertNotNull(traceInfo);
            Assert.assertEquals("echo:how do I clean the stove?", pagedResult.getItems().get(3).getText());
            Assert.assertEquals("bar", pagedResult.getItems().get(4).getText());
            Assert.assertEquals("echo:bar", pagedResult.getItems().get(6).getText());
            for (Activity activity : pagedResult.getItems()) {
                Assert.assertFalse(StringUtils.isBlank(activity.getId()));
                // Assert.assertTrue(activity.getTimestamp() > );
            }
            return null;
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityEmptyText() {
        // Get basic Qna
        QnAMaker qna = this.qnaReturnsAnswer();

        // No text
        TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity_EmptyText", null, null));
        Activity activity = new Activity() {
            {
                setType(ActivityTypes.MESSAGE);
                setText(new String());
                setConversation(new ConversationAccount());
                setRecipient(new ChannelAccount());
                setFrom(new ChannelAccount());
            }
        };
        TurnContext context = new TurnContextImpl(adapter, activity);
        Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(context, null));

        return CompletableFuture.completedFuture(null);
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityNullText() {
        // Get basic Qna
        QnAMaker qna = this.qnaReturnsAnswer();

        // No text
        TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity_NullText", null, null));
        Activity activity = new Activity() {
            {
                setType(ActivityTypes.MESSAGE);
                setText(null);
                setConversation(new ConversationAccount());
                setRecipient(new ChannelAccount());
                setFrom(new ChannelAccount());
            }
        };

        TurnContext context = new TurnContextImpl(adapter, activity);
        Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(context, null));

        return CompletableFuture.completedFuture(null);
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityNullContext() {
        // Get basic Qna
        QnAMaker qna = this.qnaReturnsAnswer();

        Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(null, null));

        return CompletableFuture.completedFuture(null);
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityBadMessage() {
        // Get basic Qna
        QnAMaker qna = this.qnaReturnsAnswer();

        // No text
        TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity_BadMessage", null, null));
        Activity activity = new Activity() {
            {
                setType(ActivityTypes.TRACE);
                setText("My Text");
                setConversation(new ConversationAccount());
                setRecipient(new ChannelAccount());
                setFrom(new ChannelAccount());
            }
        };

        TurnContext context = new TurnContextImpl(adapter, activity);
        Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(context, null));

        return CompletableFuture.completedFuture(null);
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityNullActivity() {
        // Get basic Qna
        QnAMaker qna = this.qnaReturnsAnswer();

        // No text
        TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity_NullActivity", null, null));
        TurnContext context = new MyTurnContext(adapter, null);

        Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(context, null));

        return CompletableFuture.completedFuture(null);
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswer() {
        QnAMaker qna = this.qnaReturnsAnswer();
        return qna.getAnswers(getContext("how do I clean the stove?"), null).thenApply(results -> {
            Assert.assertNotNull(results);
            Assert.assertTrue(results.length == 1);
            Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
            return null;
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerRaw() {
        QnAMaker qna = this.qnaReturnsAnswer();
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(1);
            }
        };
        return qna.getAnswersRaw(getContext("how do I clean the stove?"), options, null, null).thenApply(results -> {
            Assert.assertNotNull(results.getAnswers());
            Assert.assertTrue(results.getActiveLearningEnabled());
            Assert.assertTrue(results.getAnswers().length == 1);
            Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results.getAnswers()[0].getAnswer());

            return null;
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerLowScoreVariation() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_TopNAnswer.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnaMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setTop(5);
            }
        };
        QnAMaker qna = new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, mockHttp);
        return qna.getAnswers(getContext("Q11"), null).thenApply(results -> {
            Assert.assertNotNull(results);
            Assert.assertEquals(4, results.length);

            QueryResult[] filteredResults = qna.getLowScoreVariation(results);
            Assert.assertNotNull(filteredResults);
            Assert.assertEquals(3, filteredResults.length);
            return null;
        }).thenCompose(task -> {
            Mockito.doReturn(this.getResponse("QnaMaker_TopNAnswer_DisableActiveLearning.json"))
                    .when(mockHttp.newCall(request));
            return qna.getAnswers(getContext("Q11"), null).thenApply(results -> {
                Assert.assertNotNull(results);
                Assert.assertEquals(4, results.length);

                QueryResult[] filteredResults = qna.getLowScoreVariation(results);
                Assert.assertNotNull(filteredResults);
                Assert.assertEquals(3, filteredResults.length);
                return null;
            });
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerCallTrain() throws IOException {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("{ }")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnaMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMaker qna = new QnAMaker(qnaMakerEndpoint, null, mockHttp);
        FeedbackRecords feedbackRecords = new FeedbackRecords();

        FeedbackRecord feedback1 = new FeedbackRecord() {
            {
                setQnaId(1);
                setUserId("test");
                setUserQuestion("How are you?");
            }
        };

        FeedbackRecord feedback2 = new FeedbackRecord() {
            {
                setQnaId(2);
                setUserId("test");
                setUserQuestion("What up??");
            }
        };

        feedbackRecords.setRecords(new FeedbackRecord[] { feedback1, feedback2 });

        return qna.callTrain(feedbackRecords);
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerConfiguration() {
        QnAMaker qna = this.qnaReturnsAnswer();
        return qna.getAnswers(getContext("how do I clean the stove?"), null).thenApply(results -> {
            Assert.assertNotNull(results);
            Assert.assertTrue(results.length == 1);
            Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
            return null;
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerWithFiltering() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_UsesStrictFilters_ToReturnAnswer.json"))
                .when(mockHttp.newCall(request));
        QnAMakerEndpoint qnaMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setStrictFilters(new Metadata[] { new Metadata() {
                    {
                        setName("topic");
                        setValue("value");
                    }
                } });
                setTop(1);
            }
        };
        QnAMaker qna = new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, mockHttp);

        return qna.getAnswers(getContext("how do I clean the stove?"), qnaMakerOptions).thenApply(results -> {
            Assert.assertNotNull(results);
            Assert.assertTrue(results.length == 1);
            Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
            Assert.assertEquals("topic", results[0].getMetadata()[0].getName());
            Assert.assertEquals("value", results[0].getMetadata()[0].getValue());

            // verify we are actually passing on the options
            // var obj = JObject.Parse(interceptHttp.Content);
            // Assert.Equal(1, obj["top"].Value<int>());
            // Assert.Equal("topic", obj["strictFilters"][0]["name"].Value<string>());
            // Assert.Equal("value", obj["strictFilters"][0]["value"].Value<string>());
            return null;
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerSetScoreThresholdWhenThresholdIsZero() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnaMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setScoreThreshold(0.0f);
            }
        };
        QnAMaker qnaWithZeroValueThreshold = new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, mockHttp);

        return qnaWithZeroValueThreshold.getAnswers(getContext("how do I clean the stove?"), new QnAMakerOptions() {
            {
                setTop(1);
            }
        }).thenApply(results -> {
            Assert.assertNotNull(results);
            Assert.assertTrue(results.length == 1);
            return null;
        });
    }

    private static TurnContext getContext(String utterance) {
        TestAdapter b = new TestAdapter();
        Activity a = new Activity() {
            {
                setType(ActivityTypes.MESSAGE);
                setText(utterance);
                setConversation(new ConversationAccount());
                setRecipient(new ChannelAccount());
                setFrom(new ChannelAccount());
            }
        };

        return new TurnContextImpl(b, a);
    }

    private QnAMaker qnaReturnsAnswer() {
        // Mock Qna
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnaMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setTop(1);
            }
        };
        return new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, mockHttp);
    }

    private String getResponse(String fileName) {
        Path path = this.getFilePath(fileName);
        try {
            return Files.readAllBytes(path).toString();
        } catch (IOException e) {
            LoggerFactory.getLogger(QnAMakerTests.class).error(String.format("Cannot read the file: %s", fileName));
            return "";
        }
    }

    private Path getFilePath(String fileName) {
        return Paths.get(System.getProperty("user.dir"), "TestData", fileName);
    }
}
