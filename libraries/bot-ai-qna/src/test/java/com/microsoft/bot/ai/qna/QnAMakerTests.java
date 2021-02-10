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
import com.microsoft.bot.ai.qna.models.QnARequestContext;
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

import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

public class QnAMakerTests {
    private final String knowledgeBaseId = "dummy-id";
    private final String endpointKey = "dummy-key";
    private final String hostname = "https://dummy-hostname.azurewebsites.net/qnamaker";

    private String getRequestUrl() {
        return String.format("%1$s/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
    }

    private String getV2LegacyRequestUrl() {
        return String.format("%1$s/v2.0/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
    }

    private String getV3LegacyRequestUrl() {
        return String.format("%1$s/v3.0/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
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

    @Test
    public CompletableFuture<Void> qnaMakerTestThreshold() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_TestThreshold.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setTop(1);
                setScoreThreshold(0.99F);
            }
        };
        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, qnaMakerOptions, mockHttp);

        return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
            Assert.assertNotNull(results);
            Assert.assertTrue(results.length == 0);
        });
    }

    @Test
    public void qnaMakerTestScoreThresholdTooLargeOutOfRange() {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions tooLargeThreshold = new QnAMakerOptions() {
            {
                setTop(1);
                setScoreThreshold(1.1F);
            }
        };
        Assert.assertThrows(IllegalArgumentException.class, () -> new QnAMaker(qnAMakerEndpoint, tooLargeThreshold, null));
    }

    @Test
    public void qnaMakerTestScoreThresholdTooSmallOutOfRange() {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions tooSmallThreshold = new QnAMakerOptions() {
            {
                setTop(1);
                setScoreThreshold(1.1F);
            }
        };
        Assert.assertThrows(IllegalArgumentException.class, () -> new QnAMaker(qnAMakerEndpoint, tooSmallThreshold, null));
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerWithContext() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswerWithContext.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnARequestContext context = new QnARequestContext() {
            {
                setPreviousQnAId(5);
                setPreviousUserQuery("how do I clean the stove?");
            }
        };
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(1);
                setContext(context);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, mockHttp);

        return qna.getAnswers(getContext("Where can I buy?"), options).thenAccept(results -> {
            Assert.assertNotNull(results);
            Assert.assertTrue(results.length == 1);
            Assert.assertEquals(55, (int) results[0].getId());
            Assert.assertEquals(1, (double) results[0].getScore());
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnAnswersWithoutContext() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswerWithoutContext.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(3);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, mockHttp);

        return qna.getAnswers(getContext("Where can I buy?"), options).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertEquals(2, results.length);
           Assert.assertNotEquals((float) 1, results[0].getScore());
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsHighScoreWhenIdPassed() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswerWithContext.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(1);
                setQnAId(55);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, mockHttp);
        return qna.getAnswers(getContext("Where can I buy?"), options).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertTrue(results.length == 1);
           Assert.assertEquals(55, (int) results[0].getId());
           Assert.assertEquals(1, (double) results[0].getScore());
        });
    }

    @Test
    public void qnaMakerTestTopOutOfRange() {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(-1);
                setScoreThreshold(0.5F);
            }
        };
        Assert.assertThrows(IllegalArgumentException.class, () -> new QnAMaker(qnAMakerEndpoint, options, null));
    }

    @Test
    public void qnaMakerTestEndpointEmptyKbId() {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId("");
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        Assert.assertThrows(IllegalArgumentException.class, () -> new QnAMaker(qnAMakerEndpoint, null, null));
    }

    @Test
    public void qnaMakerTestEndpointEmptyEndpointKey() {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey("");
                setHost(hostname);
            }
        };
        Assert.assertThrows(IllegalArgumentException.class, () -> new QnAMaker(qnAMakerEndpoint, null, null));
    }

    @Test
    public void qnaMakerTestEndpointEmptyHost() {
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost("");
            }
        };
        Assert.assertThrows(IllegalArgumentException.class, () -> new QnAMaker(qnAMakerEndpoint, null, null));
    }

    @Test
    public CompletableFuture<Void> qnaMakerUserAgent() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        String userAgentHeader = request.header("User-Agent");
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(1);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, mockHttp);
        return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertTrue(results.length == 1);
           Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
               results[0].getAnswer());

            // Verify that we added the bot.builder package details.
           Assert.assertTrue(userAgentHeader.contains("Microsoft.Bot.Builder.AI.QnA/4"));
        });
    }

    @Test
    public void qnaMakerV2LegacyEndpointShouldThrow() {
        Request request = new Request.Builder().url(this.getV2LegacyRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_LegacyEndpointAnswer.json")).when(mockHttp.newCall(request));
        String host = new StringBuilder("{")
            .append(hostname)
            .append("}")
            .append("/v2.0").toString();
        QnAMakerEndpoint v2LegacyEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(host);
            }
        };

        Assert.assertThrows(UnsupportedOperationException.class, () -> new QnAMaker(v2LegacyEndpoint,null,mockHttp));
    }

    @Test
    public void qnaMakerV3LeagacyEndpointShouldThrow() {
        Request request = new Request.Builder().url(this.getV3LegacyRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_LegacyEndpointAnswer.json")).when(mockHttp.newCall(request));
        String host = new StringBuilder("{")
            .append(hostname)
            .append("}")
            .append("/v3.0").toString();
        QnAMakerEndpoint v3LegacyEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(host);
            }
        };

        Assert.assertThrows(UnsupportedOperationException.class, () -> new QnAMaker(v3LegacyEndpoint,null,mockHttp));
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerWithMetadataBoost() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswersWithMetadataBoost.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(1);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, mockHttp);

        return qna.getAnswers(getContext("who loves me?"), options).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertTrue(results.length == 1);
           Assert.assertEquals("Kiki", results[0].getAnswer());
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestThresholdInQueryOption() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer_GivenScoreThresholdQueryOption.json"))
            .when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions queryOptionsWithScoreThreshold = new QnAMakerOptions() {
            {
                setScoreThreshold(0.5F);
                setTop(2);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, queryOptionsWithScoreThreshold, mockHttp);

        return qna.getAnswers(getContext("What happens when you hug a porcupine?"),
            queryOptionsWithScoreThreshold).thenAccept(results -> {
           Assert.assertNotNull(results);
           /* TODO
           var obj = JObject.Parse(interceptHttp.Content);
            Assert.Equal(2, obj["top"].Value<int>());
            Assert.Equal(0.5F, obj["scoreThreshold"].Value<float>());
            */
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestUnsuccessfulResponse() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(HttpStatus.BAD_GATEWAY)
            .when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, null, mockHttp);

        Assert.assertThrows(HttpRequestMethodNotSupportedException.class,
            () -> qna.getAnswers(getContext("how do I clean the stove?"), null));

        return CompletableFuture.completedFuture(null);
    }

    @Test
    public CompletableFuture<Void> qnaMakerIsTestTrue() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_IsTest_True.json")).when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setTop(1);
                setIsTest(true);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, qnaMakerOptions, mockHttp);

        return qna.getAnswers(getContext("Q11"), qnaMakerOptions).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertTrue(results.length == 0);
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerRankerTypeQuestionOnly() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_RankerType_QuestionOnly.json"))
            .when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        QnAMakerOptions qnaMakerOptions = new QnAMakerOptions() {
            {
                setTop(1);
                setRankerType("QuestionOnly");
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, qnaMakerOptions, mockHttp);

        return qna.getAnswers(getContext("Q11"), qnaMakerOptions).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertEquals(2, results.length);
        });
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestOptionsHydration() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer.json"))
            .when(mockHttp.newCall(request));

        QnAMakerOptions noFiltersOptions = new QnAMakerOptions() {
            {
                setTop(30);
            }
        };
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        Metadata strictFilterMovie = new Metadata() {
            {
                setName("movie");
                setValue("disney");
            }
        };
        Metadata strictFilterHome = new Metadata() {
            {
                setName("home");
                setValue("floating");
            }
        };
        Metadata strictFilterDog = new Metadata() {
            {
                setName("dog");
                setValue("samoyed");
            }
        };
        Metadata[] oneStrictFilters = new Metadata[] {strictFilterMovie};
        Metadata[] twoStrictFilters = new Metadata[] {strictFilterMovie, strictFilterHome};
        Metadata[] allChangedRequestOptionsFilters = new Metadata[] {strictFilterDog};
        QnAMakerOptions oneFilteredOption = new QnAMakerOptions() {
            {
                setTop(30);
                setStrictFilters(oneStrictFilters);
            }
        };
        QnAMakerOptions twoStrictFiltersOptions = new QnAMakerOptions() {
            {
                setTop(30);
                setStrictFilters(twoStrictFilters);
            }
        };
        QnAMakerOptions allChangedRequestOptions = new QnAMakerOptions() {
            {
                setTop(2000);
                setScoreThreshold(0.4F);
                setStrictFilters(allChangedRequestOptionsFilters);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, noFiltersOptions, mockHttp);

        TurnContext context = getContext("up");

        // Ensure that options from previous requests do not bleed over to the next,
        // And that the options set in the constructor are not overwritten improperly by options passed into .GetAnswersAsync()

        CapturedRequest requestContent1 = null;
        CapturedRequest requestContent2 = null;
        CapturedRequest requestContent3 = null;
        CapturedRequest requestContent4 = null;
        CapturedRequest requestContent5 = null;
        CapturedRequest requestContent6 = null;

        return qna.getAnswers(context, noFiltersOptions).thenRun(() -> {
            //var requestContent1 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);
        }).thenCompose(task -> qna.getAnswers(context, twoStrictFiltersOptions).thenRun(() -> {
            //var requestContent2 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);
        })).thenCompose(task -> qna.getAnswers(context, oneFilteredOption).thenRun(() -> {
            //var requestContent3 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);
        })).thenCompose(task -> qna.getAnswers(context, null).thenRun(() -> {
            // var requestContent4 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);
        })).thenCompose(task -> qna.getAnswers(context, allChangedRequestOptions).thenRun(() -> {
            //var requestContent5 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);
        })).thenCompose(task -> qna.getAnswers(context, null).thenRun(() -> {
            // var requestContent6 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);

            Assert.assertTrue(requestContent1.getStrictFilters().length == 0);
            Assert.assertEquals(2, requestContent2.getStrictFilters().length);
            Assert.assertTrue(requestContent3.getStrictFilters().length == 1);
            Assert.assertTrue(requestContent4.getStrictFilters().length == 0);

            Assert.assertEquals(2000, (int) requestContent5.getTop());
            Assert.assertEquals(0.42, Math.round(requestContent5.getScoreThreshold()));
            Assert.assertTrue(requestContent5.getStrictFilters().length == 1);

            Assert.assertEquals(30, (int) requestContent6.getTop());
            Assert.assertEquals(0.3, Math.round(requestContent6.getScoreThreshold()));
            Assert.assertTrue(requestContent6.getStrictFilters().length == 0);
        }));
    }

    @Test
    public CompletableFuture<Void> qnaMakerStrictFiltersCompoundOperationType() {
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer.json"))
            .when(mockHttp.newCall(request));
        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };
        Metadata strictFilterMovie = new Metadata() {
            {
                setName("movie");
                setValue("disney");
            }
        };
        Metadata strictFilterProduction = new Metadata() {
            {
                setName("production");
                setValue("Walden");
            }
        };
        Metadata[] strictFilters = new Metadata[] {strictFilterMovie, strictFilterProduction};
        QnAMakerOptions oneFilteredOption = new QnAMakerOptions() {
            {
                setTop(30);
                setStrictFilters(strictFilters);
                setStrictFiltersJoinOperator(JoinOperator.OR);
            }
        };

        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, oneFilteredOption, mockHttp);

        TurnContext context = getContext("up");

        return qna.getAnswers(context, oneFilteredOption).thenAccept(noFilterResults1 -> {
           //  var requestContent1 = JsonConvert.DeserializeObject<CapturedRequest>(interceptHttp.Content);
            Assert.assertEquals(2, oneFilteredOption.getStrictFilters().length);
            Assert.assertEquals(JoinOperator.OR, oneFilteredOption.getStrictFiltersJoinOperator());
        });
    }

    @Test
    public CompletableFuture<Void> telemetryNullTelemetryClient() {
        // Arrange
        Request request = new Request.Builder().url(this.getRequestUrl()).build();
        OkHttpClient mockHttp = Mockito.mock(OkHttpClient.class);
        Mockito.doReturn(this.getResponse("QnaMaker_ReturnsAnswer.json"))
            .when(mockHttp.newCall(request));

        QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
            {
                setKnowledgeBaseId(knowledgeBaseId);
                setEndpointKey(endpointKey);
                setHost(hostname);
            }
        };

        QnAMakerOptions options = new QnAMakerOptions() {
            {
                setTop(1);
            }
        };

        // Act (Null Telemetry client)
        // This will default to the NullTelemetryClient which no-ops all calls.
        QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, mockHttp, null, true);
        return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
           Assert.assertNotNull(results);
           Assert.assertTrue(results.length == 1);
           Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack", results[0].getAnswer());
           Assert.assertEquals("Editorial", results[0].getSource());
        });
    }

    private class CapturedRequest {
        private String[] questions;
        private Integer top;
        private Metadata[] strictFilters;
        private Metadata[] MetadataBoost;
        private Float scoreThreshold;

        public String[] getQuestions() {
            return questions;
        }

        public void setQuestions(String[] questions) {
            this.questions = questions;
        }

        public Integer getTop() {
            return top;
        }

        public void setTop(Integer top) {
            this.top = top;
        }

        public Metadata[] getStrictFilters() {
            return strictFilters;
        }

        public void setStrictFilters(Metadata[] strictFilters) {
            this.strictFilters = strictFilters;
        }

        public Metadata[] getMetadataBoost() {
            return MetadataBoost;
        }

        public void setMetadataBoost(Metadata[] metadataBoost) {
            MetadataBoost = metadataBoost;
        }

        public Float getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(Float scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }
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
