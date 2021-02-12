// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.ai.qna;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.ai.qna.dialogs.QnAMakerDialog;
import com.microsoft.bot.ai.qna.models.FeedbackRecord;
import com.microsoft.bot.ai.qna.models.FeedbackRecords;
import com.microsoft.bot.ai.qna.models.Metadata;
import com.microsoft.bot.ai.qna.models.QnAMakerTraceInfo;
import com.microsoft.bot.ai.qna.models.QnARequestContext;
import com.microsoft.bot.ai.qna.models.QueryResult;
import com.microsoft.bot.ai.qna.utils.QnATelemetryConstants;
import com.microsoft.bot.builder.*;
import com.microsoft.bot.builder.adapters.TestAdapter;
import com.microsoft.bot.builder.adapters.TestFlow;

import com.microsoft.bot.dialogs.*;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.ConversationAccount;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;

import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class QnAMakerTests {
    private final String knowledgeBaseId = "dummy-id";
    private final String endpointKey = "dummy-key";
    private final String hostname = "https://dummy-hostname.azurewebsites.net/qnamaker";

    @Captor
    ArgumentCaptor<String> eventNameCaptor;

    @Captor
    ArgumentCaptor<Map<String, String>> propertiesCaptor;

    private String getRequestUrl() {
        return String.format("%1$s/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
    }

    private String getV2LegacyRequestUrl() {
        return String.format("%1$s/v2.0/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
    }

    private String getV3LegacyRequestUrl() {
        return String.format("%1$s/v3.0/knowledgebases/%2$s/generateanswer", hostname, knowledgeBaseId);
    }

    private String getTrainRequestUrl() {
        return String.format("%1$s/v3.0/knowledgebases/%2$s/train", hostname, knowledgeBaseId);
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivity() {
        // Mock Qna
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            QnAMaker qna = this.qnaReturnsAnswer();

            // Invoke flow which uses mock
            MemoryTranscriptStore transcriptStore = new MemoryTranscriptStore();
            TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity", null, null))
                .use(new TranscriptLoggerMiddleware(transcriptStore));
            final String[] conversationId = {null};
            new TestFlow(adapter, (turnContext -> {
                // Simulate Qna Lookup
                if(turnContext.getActivity().getText().compareTo("how do I clean the stove?") == 0) {
                    qna.getAnswers(turnContext, null).thenAccept(results -> {
                        Assert.assertNotNull(results);
                        Assert.assertTrue(results.length == 1);
                        Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                            results[0].getAnswer());
                    }).thenApply(task -> {
                        conversationId[0] = turnContext.getActivity().getConversation().getId();
                        Activity typingActivity = new Activity() {
                            {
                                setType(ActivityTypes.TYPING);
                                setRelatesTo(turnContext.getActivity().getRelatesTo());
                            }
                        };
                        return typingActivity;
                    }).thenAccept(typingActivity -> {
                        turnContext.sendActivity(typingActivity);
                    }).thenAccept(task -> {
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            // no handle exception
                        }
                    }).thenAccept(task -> {
                        turnContext.sendActivity(String.format("echo: %s", turnContext.getActivity().getText()));
                    });
                }
                return null;
            }))
                .send("how do I clean the stove?").assertReply(activity -> {
                Assert.assertEquals(activity.getType(), ActivityTypes.TYPING);

            }).assertReply("echo:how do I clean the stove?").send("bar")
                .assertReply(activity -> Assert.assertEquals(activity.getType(), ActivityTypes.TYPING))
                .assertReply("echo:bar").startTest().join();
            // Validate Trace Activity created
            return transcriptStore.getTranscriptActivities("test", conversationId[0]).thenAccept(pagedResult -> {
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
                    // default(DateTimeOffset) in C# is 1/1/0001 12:00:00 AM + 00:00 we need to test it
                    //Assert.assertTrue(activity.getTimestamp() > ));
                }
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityEmptyText() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityNullText() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityNullContext() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            // Get basic Qna
            QnAMaker qna = this.qnaReturnsAnswer();

            Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(null, null));

            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityBadMessage() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTraceActivityNullActivity() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            // Get basic Qna
            QnAMaker qna = this.qnaReturnsAnswer();

            // No text
            TestAdapter adapter = new TestAdapter(
                TestAdapter.createConversationReference("QnaMaker_TraceActivity_NullActivity", null, null));
            TurnContext context = new MyTurnContext(adapter, null);

            Assert.assertThrows(IllegalArgumentException.class, () -> qna.getAnswers(context, null));

            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswer() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            QnAMaker qna = this.qnaReturnsAnswer();
            return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerRaw() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            QnAMaker qna = this.qnaReturnsAnswer();
            QnAMakerOptions options = new QnAMakerOptions() {
                {
                    setTop(1);
                }
            };
            return qna.getAnswersRaw(getContext("how do I clean the stove?"), options, null, null).thenAccept(results -> {
                Assert.assertNotNull(results.getAnswers());
                Assert.assertTrue(results.getActiveLearningEnabled());
                Assert.assertTrue(results.getAnswers().length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results.getAnswers()[0].getAnswer());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerLowScoreVariation() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_TopNAnswer.json");
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
            QnAMaker qna = new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, new OkHttpClient());
            return qna.getAnswers(getContext("Q11"), null).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertEquals(4, results.length);

                QueryResult[] filteredResults = qna.getLowScoreVariation(results);
                Assert.assertNotNull(filteredResults);
                Assert.assertEquals(3, filteredResults.length);
            }).thenCompose(task -> {
                this.configureMockServer(mockWebServer, "QnaMaker_TopNAnswer_DisableActiveLearning.json");
                return qna.getAnswers(getContext("Q11"), null).thenAccept(results -> {
                    Assert.assertNotNull(results);
                    Assert.assertEquals(4, results.length);

                    QueryResult[] filteredResults = qna.getLowScoreVariation(results);
                    Assert.assertNotNull(filteredResults);
                    Assert.assertEquals(3, filteredResults.length);
                });
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {

            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerCallTrain() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer,"{ }");
            QnAMakerEndpoint qnaMakerEndpoint = new QnAMakerEndpoint() {
                {
                    setKnowledgeBaseId(knowledgeBaseId);
                    setEndpointKey(endpointKey);
                    setHost(hostname);
                }
            };
            QnAMaker qna = new QnAMaker(qnaMakerEndpoint, null, new OkHttpClient());
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
        } catch (IOException ex) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerConfiguration() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            QnAMaker qna = this.qnaReturnsAnswer();
            return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerWithFiltering() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_UsesStrictFilters_ToReturnAnswer.json");
            RecordedRequest request = mockWebServer.takeRequest();
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
            QnAMaker qna = new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, new OkHttpClient());
            ObjectMapper objectMapper = new ObjectMapper();

            return qna.getAnswers(getContext("how do I clean the stove?"), qnaMakerOptions).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
                Assert.assertEquals("topic", results[0].getMetadata()[0].getName());
                Assert.assertEquals("value", results[0].getMetadata()[0].getValue());

                CapturedRequest obj = new CapturedRequest();
                try {
                    obj = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // verify we are actually passing on the options
                Assert.assertEquals(1, (int) obj.getTop());
                Assert.assertEquals("topic", obj.getStrictFilters()[0].getName());
                Assert.assertEquals("value", obj.getStrictFilters()[0].getValue());
            });
        } catch (InterruptedException ex) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerSetScoreThresholdWhenThresholdIsZero() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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
            QnAMaker qnaWithZeroValueThreshold = new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, new OkHttpClient());

            return qnaWithZeroValueThreshold.getAnswers(getContext("how do I clean the stove?"), new QnAMakerOptions() {
                {
                    setTop(1);
                }
            }).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestThreshold() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_TestThreshold.json");
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
            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, qnaMakerOptions, new OkHttpClient());

            return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 0);
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswerWithContext.json");
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient());

            return qna.getAnswers(getContext("Where can I buy?"), options).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals(55, (int) results[0].getId());
                Assert.assertEquals(1, (double) results[0].getScore(), 2);
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnAnswersWithoutContext() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswerWithoutContext.json");
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient());

            return qna.getAnswers(getContext("Where can I buy?"), options).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertEquals(2, results.length);
                Assert.assertNotEquals((float) 1, results[0].getScore());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsHighScoreWhenIdPassed() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswerWithContext.json");
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient());
            return qna.getAnswers(getContext("Where can I buy?"), options).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals(55, (int) results[0].getId());
                Assert.assertEquals(1, (double) results[0].getScore(), 2);
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            RecordedRequest request = mockWebServer.takeRequest();
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient());
            return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());

                // Verify that we added the bot.builder package details.
                Assert.assertTrue(request.getHeader("User-Agent").contains("Microsoft.Bot.Builder.AI.QnA/4"));
            });
        } catch (InterruptedException ex) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void qnaMakerV2LegacyEndpointShouldThrow() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer,"QnaMaker_LegacyEndpointAnswer.json");
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

            Assert.assertThrows(UnsupportedOperationException.class, () -> new QnAMaker(v2LegacyEndpoint,null, new OkHttpClient()));
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void qnaMakerV3LeagacyEndpointShouldThrow() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_LegacyEndpointAnswer.json");
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

            Assert.assertThrows(UnsupportedOperationException.class, () -> new QnAMaker(v3LegacyEndpoint,null, new OkHttpClient()));
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerReturnsAnswerWithMetadataBoost() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswersWithMetadataBoost.json");
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient());

            return qna.getAnswers(getContext("who loves me?"), options).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("Kiki", results[0].getAnswer());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestThresholdInQueryOption() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer_GivenScoreThresholdQueryOption.json");
            RecordedRequest request = mockWebServer.takeRequest();
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, queryOptionsWithScoreThreshold, new OkHttpClient());

            ObjectMapper objectMapper = new ObjectMapper();

            return qna.getAnswers(getContext("What happens when you hug a porcupine?"),
                queryOptionsWithScoreThreshold).thenAccept(results -> {
                Assert.assertNotNull(results);

                CapturedRequest obj = new CapturedRequest();
                try {
                    obj = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Assert.assertEquals(2, (int) obj.getTop());
                Assert.assertEquals(0.5, (float) obj.getScoreThreshold(), 2);
            });
        } catch (InterruptedException ex) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestUnsuccessfulResponse() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            mockWebServer = this.initializeMockServer(HttpStatus.BAD_GATEWAY);
            QnAMakerEndpoint qnAMakerEndpoint = new QnAMakerEndpoint() {
                {
                    setKnowledgeBaseId(knowledgeBaseId);
                    setEndpointKey(endpointKey);
                    setHost(hostname);
                }
            };

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, null, new OkHttpClient());

            Assert.assertThrows(HttpRequestMethodNotSupportedException.class,
                () -> qna.getAnswers(getContext("how do I clean the stove?"), null));

            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerIsTestTrue() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_IsTest_True.json");
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, qnaMakerOptions, new OkHttpClient());

            return qna.getAnswers(getContext("Q11"), qnaMakerOptions).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 0);
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerRankerTypeQuestionOnly() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_RankerType_QuestionOnly.json");
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, qnaMakerOptions, new OkHttpClient());

            return qna.getAnswers(getContext("Q11"), qnaMakerOptions).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertEquals(2, results.length);
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerTestOptionsHydration() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            RecordedRequest request = mockWebServer.takeRequest();

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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, noFiltersOptions, new OkHttpClient());

            TurnContext context = getContext("up");

            // Ensure that options from previous requests do not bleed over to the next,
            // And that the options set in the constructor are not overwritten improperly by options passed into .GetAnswersAsync()

            final CapturedRequest[] requestContent = {null, null, null, null, null, null};
            ObjectMapper objectMapper = new ObjectMapper();

            return qna.getAnswers(context, noFiltersOptions).thenRun(() -> {
                try {
                    requestContent[0] = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).thenCompose(task -> qna.getAnswers(context, twoStrictFiltersOptions).thenRun(() -> {
                try {
                    requestContent[1] = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })).thenCompose(task -> qna.getAnswers(context, oneFilteredOption).thenRun(() -> {
                try {
                    requestContent[2] = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })).thenCompose(task -> qna.getAnswers(context, null).thenRun(() -> {
                try {
                    requestContent[3] = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })).thenCompose(task -> qna.getAnswers(context, allChangedRequestOptions).thenRun(() -> {
                try {
                    requestContent[4] = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })).thenCompose(task -> qna.getAnswers(context, null).thenRun(() -> {
                try {
                    requestContent[5] = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Assert.assertTrue(requestContent[0].getStrictFilters().length == 0);
                Assert.assertEquals(2, requestContent[1].getStrictFilters().length);
                Assert.assertTrue(requestContent[2].getStrictFilters().length == 1);
                Assert.assertTrue(requestContent[3].getStrictFilters().length == 0);

                Assert.assertEquals(2000, (int) requestContent[4].getTop());
                Assert.assertEquals(0.42, Math.round(requestContent[4].getScoreThreshold()));
                Assert.assertTrue(requestContent[4].getStrictFilters().length == 1);

                Assert.assertEquals(30, (int) requestContent[5].getTop());
                Assert.assertEquals(0.3, Math.round(requestContent[5].getScoreThreshold()),2);
                Assert.assertTrue(requestContent[5].getStrictFilters().length == 0);
            }));
        } catch (InterruptedException ex) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> qnaMakerStrictFiltersCompoundOperationType() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
            RecordedRequest request = mockWebServer.takeRequest();
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

            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, oneFilteredOption, new OkHttpClient());

            TurnContext context = getContext("up");
            ObjectMapper objectMapper = new ObjectMapper();

            return qna.getAnswers(context, oneFilteredOption).thenAccept(noFilterResults1 -> {
                CapturedRequest requestContent;
                try {
                    requestContent = objectMapper.readValue(request.getBody().readUtf8(), CapturedRequest.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Assert.assertEquals(2, oneFilteredOption.getStrictFilters().length);
                Assert.assertEquals(JoinOperator.OR, oneFilteredOption.getStrictFiltersJoinOperator());
            });
        } catch (InterruptedException ex) {
            Assert.assertFalse(true);
            return CompletableFuture.completedFuture(null);
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryNullTelemetryClient() {
        // Arrange
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");

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
            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient(), null, true);
            return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack", results[0].getAnswer());
                Assert.assertEquals("Editorial", results[0].getSource());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryReturnsAnswer() {
        // Arrange
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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

            BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);

            // Act - See if we get data back in telemetry
            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient(), telemetryClient, true);
            return qna.getAnswers(getContext("what is the answer to my nonsense question?"), null)
                .thenAccept(results -> {
                    // Assert - Check Telemetry logged
                    // verify BotTelemetryClient was invoked 1 times, and capture arguments.
                    verify(telemetryClient, times(1)).trackEvent(
                        eventNameCaptor.capture(),
                        propertiesCaptor.capture()
                    );
                    List<String> eventNames = eventNameCaptor.getAllValues();
                    List<Map<String, String>> properties = propertiesCaptor.getAllValues();
                    Assert.assertEquals(3, eventNames.size());
                    Assert.assertEquals(eventNames.get(0), QnATelemetryConstants.QNA_MSG_EVENT);
                    Assert.assertTrue(properties.get(0).containsKey("knowledgeBaseId"));
                    Assert.assertTrue(properties.get(0).containsKey("matchedQuestion"));
                    Assert.assertEquals("No Qna Question matched", properties.get(0).get("matchedQuestion"));
                    Assert.assertTrue(properties.get(0).containsKey("question"));
                    Assert.assertTrue(properties.get(0).containsKey("questionId"));
                    Assert.assertTrue(properties.get(0).containsKey("answer"));
                    Assert.assertEquals("No Qna Question matched", properties.get(0).get("answer"));
                    Assert.assertTrue(properties.get(0).containsKey("articleFound"));
                    Assert.assertTrue(properties.get(1) == null);

                    // Assert - Validate we didn't break QnA functionality.
                    Assert.assertNotNull(results);
                    Assert.assertTrue(results.length == 0);
                });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryPii() {
        // Arrange
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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

            BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);

            // Act
            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient(), telemetryClient, false);
            return qna.getAnswers(getContext("how do I clean the stove?"), null).thenAccept(results -> {
                // verify BotTelemetryClient was invoked 3 times, and capture arguments.
                verify(telemetryClient, times(3)).trackEvent(
                    eventNameCaptor.capture(),
                    propertiesCaptor.capture()
                );
                List<String> eventNames = eventNameCaptor.getAllValues();
                List<Map<String, String>> properties = propertiesCaptor.getAllValues();

                Assert.assertEquals(3, eventNames.size());
                Assert.assertEquals(eventNames.get(0), QnATelemetryConstants.QNA_MSG_EVENT);
                Assert.assertTrue(properties.get(0).containsKey("knowledgeBaseId"));
                Assert.assertTrue(properties.get(0).containsKey("matchedQuestion"));
                Assert.assertTrue(properties.get(0).containsKey("question"));
                Assert.assertTrue(properties.get(0).containsKey("questionId"));
                Assert.assertTrue(properties.get(0).containsKey("answer"));
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    properties.get(0).get("answer"));
                Assert.assertTrue(properties.get(0).containsKey("articleFound"));
                Assert.assertTrue(eventNames.get(2).length() == 1);
                Assert.assertTrue(eventNames.get(2).contains("score"));

                // Assert - Validate we didn't break QnA functionality.
                Assert.assertNotNull(results);
                Assert.assertTrue(results.length == 1);
                Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                    results[0].getAnswer());
                Assert.assertEquals("Editorial", results[0].getSource());
            });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryOverride() {
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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

            BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);

            // Act - Override the QnaMaker object to log custom stuff and honor parms passed in.
            Map<String, String> telemetryProperties = new HashMap<String, String>();
            telemetryProperties.put("Id", "MyID");

            QnAMaker qna = new OverrideTelemetry(qnAMakerEndpoint, options, new OkHttpClient(), telemetryClient, false);
            return qna.getAnswers(getContext("how do I clean the stove?"), null, telemetryProperties, null)
                .thenAccept(results -> {
                    // verify BotTelemetryClient was invoked 2 times, and capture arguments.
                    verify(telemetryClient, times(2)).trackEvent(
                        eventNameCaptor.capture(),
                        propertiesCaptor.capture()
                    );
                    List<String> eventNames = eventNameCaptor.getAllValues();
                    List<Map<String, String>> properties = propertiesCaptor.getAllValues();

                    Assert.assertEquals(3, eventNames.size());
                    Assert.assertEquals(eventNames.get(0), QnATelemetryConstants.QNA_MSG_EVENT);
                    Assert.assertTrue(properties.get(0).size() == 2);
                    Assert.assertTrue(properties.get(0).containsKey("MyImportantProperty"));
                    Assert.assertEquals("myImportantValue", properties.get(0).get("MyImportantProperty"));
                    Assert.assertTrue(properties.get(0).containsKey("Id"));
                    Assert.assertEquals("MyID", properties.get(0).get("Id"));

                    Assert.assertEquals("MySecondEvent", eventNames.get(1));
                    Assert.assertTrue(properties.get(1).containsKey("MyImportantProperty2"));
                    Assert.assertEquals("myImportantValue2", properties.get(1).get("MyImportantProperty2"));

                    // Validate we didn't break QnA functionality.
                    Assert.assertNotNull(results);
                    Assert.assertTrue(results.length == 1);
                    Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                        results[0].getAnswer());
                    Assert.assertEquals("Editorial", results[0].getSource());
                });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryAdditionalPropsMetrics() {
        //Arrange
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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

            BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);

            // Act - Pass in properties during QnA invocation
            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient(), telemetryClient, false);
            Map<String, String> telemetryProperties = new HashMap<String, String>() {
                {
                    put("MyImportantProperty", "myImportantValue");
                }
            };
            Map<String, Double> telemetryMetrics = new HashMap<String, Double>() {
                {
                    put("MyImportantMetric", 3.14159);
                }
            };

            return qna.getAnswers(getContext("how do I clean the stove?"), null, telemetryProperties, telemetryMetrics)
                .thenAccept(results -> {
                    // Assert - added properties were added.
                    // verify BotTelemetryClient was invoked 1 times, and capture arguments.
                    verify(telemetryClient, times(1)).trackEvent(
                        eventNameCaptor.capture(),
                        propertiesCaptor.capture()
                    );
                    List<String> eventNames = eventNameCaptor.getAllValues();
                    List<Map<String, String>> properties = propertiesCaptor.getAllValues();

                    Assert.assertEquals(3, eventNames.size());
                    Assert.assertEquals(eventNames.get(0), QnATelemetryConstants.QNA_MSG_EVENT);
                    Assert.assertTrue(properties.get(0).containsKey(QnATelemetryConstants.KNOWLEDGE_BASE_ID_PROPERTY));
                    Assert.assertFalse(properties.get(0).containsKey(QnATelemetryConstants.QUESTION_PROPERTY));
                    Assert.assertTrue(properties.get(0).containsKey(QnATelemetryConstants.MATCHED_QUESTION_PROPERTY));
                    Assert.assertTrue(properties.get(0).containsKey(QnATelemetryConstants.QUESTION_ID_PROPERTY));
                    Assert.assertTrue(properties.get(0).containsKey(QnATelemetryConstants.ANSWER_PROPERTY));
                    Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                        properties.get(0).get("answer"));
                    Assert.assertTrue(properties.get(0).containsKey("MyImportantProperty"));
                    Assert.assertEquals("myImportantValue", properties.get(0).get("MyImportantProperty"));

                    Assert.assertEquals(2, properties.get(0).size());
                    Assert.assertTrue(properties.get(0).containsKey("score"));
                    Assert.assertTrue(properties.get(0).containsKey("MyImportantMetric"));
                    Assert.assertEquals(3.14159,
                        properties.get(0).get("MyImportantMetric"));

                    // Validate we didn't break QnA functionality.
                    Assert.assertNotNull(results);
                    Assert.assertTrue(results.length == 1);
                    Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                        results[0].getAnswer());
                    Assert.assertEquals("Editorial", results[0].getSource());
                });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryAdditionalPropsOverride() {
        //Arrange
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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

            BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);

            // Act - Pass in properties during QnA invocation that override default properties
            //  NOTE: We are invoking this with PII turned OFF, and passing a PII property (originalQuestion).
            QnAMaker qna = new QnAMaker(qnAMakerEndpoint, options, new OkHttpClient(), telemetryClient, false);
            Map<String, String> telemetryProperties = new HashMap<String, String>() {
                {
                    put("knowledgeBaseId", "myImportantValue");
                    put("originalQuestion", "myImportantValue2");
                }
            };
            Map<String, Double> telemetryMetrics = new HashMap<String, Double>() {
                {
                    put("score", 3.14159);
                }
            };

            return qna.getAnswers(getContext("how do I clean the stove?"), null, telemetryProperties, telemetryMetrics)
                .thenAccept(results -> {
                    // Assert - added properties were added.
                    // verify BotTelemetryClient was invoked 1 times, and capture arguments.
                    verify(telemetryClient, times(1)).trackEvent(
                        eventNameCaptor.capture(),
                        propertiesCaptor.capture()
                    );
                    List<String> eventNames = eventNameCaptor.getAllValues();
                    List<Map<String, String>> properties = propertiesCaptor.getAllValues();

                    Assert.assertEquals(3, eventNames.size());
                    Assert.assertEquals(eventNames.get(0), QnATelemetryConstants.QNA_MSG_EVENT);
                    Assert.assertTrue(properties.get(0).containsKey("knowledgeBaseId"));
                    Assert.assertEquals("myImportantValue", properties.get(0).get("knowledgeBaseId"));
                    Assert.assertTrue(properties.get(0).containsKey("matchedQuestion"));
                    Assert.assertEquals("myImportantValue2", properties.get(0).get("originalQuestion"));
                    Assert.assertFalse(properties.get(0).containsKey("question"));
                    Assert.assertTrue(properties.get(0).containsKey("questionId"));
                    Assert.assertTrue(properties.get(0).containsKey("answer"));
                    Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                        properties.get(0).get("answer"));
                    Assert.assertFalse(properties.get(0).containsKey("MyImportantProperty"));

                    Assert.assertEquals(1, properties.get(0).size());
                    Assert.assertTrue(properties.get(0).containsKey("score"));
                    Assert.assertEquals(3.14159,
                        properties.get(0).get("score"));
                });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public CompletableFuture<Void> telemetryFillPropsOverride() {
        //Arrange
        MockWebServer mockWebServer = new MockWebServer();
        try {
            this.configureMockServer(mockWebServer, "QnaMaker_ReturnsAnswer.json");
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

            BotTelemetryClient telemetryClient = Mockito.mock(BotTelemetryClient.class);

            // Act - Pass in properties during QnA invocation that override default properties
            //       In addition Override with derivation.  This presents an interesting question of order of setting properties.
            //       If I want to override "originalQuestion" property:
            //           - Set in "Stock" schema
            //           - Set in derived QnAMaker class
            //           - Set in GetAnswersAsync
            //       Logically, the GetAnswersAync should win.  But ultimately OnQnaResultsAsync decides since it is the last
            //       code to touch the properties before logging (since it actually logs the event).
            QnAMaker qna = new OverrideFillTelemetry(qnAMakerEndpoint, options, new OkHttpClient(), telemetryClient, false);
            Map<String, String> telemetryProperties = new HashMap<String, String>() {
                {
                    put("knowledgeBaseId", "myImportantValue");
                    put("matchedQuestion", "myImportantValue2");
                }
            };
            Map<String, Double> telemetryMetrics = new HashMap<String, Double>() {
                {
                    put("score", 3.14159);
                }
            };

            return qna.getAnswers(getContext("how do I clean the stove?"), null, telemetryProperties, telemetryMetrics)
                .thenAccept(results -> {
                    // Assert - added properties were added.
                    // verify BotTelemetryClient was invoked 2 times, and capture arguments.
                    verify(telemetryClient, times(2)).trackEvent(
                        eventNameCaptor.capture(),
                        propertiesCaptor.capture()
                    );
                    List<String> eventNames = eventNameCaptor.getAllValues();
                    List<Map<String, String>> properties = propertiesCaptor.getAllValues();

                    Assert.assertEquals(3, eventNames.size());
                    Assert.assertEquals(eventNames.get(0), QnATelemetryConstants.QNA_MSG_EVENT);
                    Assert.assertEquals(6, properties.get(0).size());
                    Assert.assertTrue(properties.get(0).containsKey("knowledgeBaseId"));
                    Assert.assertEquals("myImportantValue", properties.get(0).get("knowledgeBaseId"));
                    Assert.assertTrue(properties.get(0).containsKey("matchedQuestion"));
                    Assert.assertEquals("myImportantValue2", properties.get(0).get("matchedQuestion"));
                    Assert.assertTrue(properties.get(0).containsKey("questionId"));
                    Assert.assertTrue(properties.get(0).containsKey("answer"));
                    Assert.assertEquals("BaseCamp: You can use a damp rag to clean around the Power Pack",
                        properties.get(0).get("answer"));
                    Assert.assertTrue(properties.get(0).containsKey("articleFound"));
                    Assert.assertTrue(properties.get(0).containsKey("MyImportantProperty"));
                    Assert.assertEquals("myImportantValue", properties.get(0).get("MyImportantProperty"));

                    Assert.assertEquals(1, properties.get(0).size());
                    Assert.assertTrue(properties.get(0).containsKey("score"));
                    Assert.assertEquals(3.14159,
                        properties.get(0).get("score"));
                });
        } finally {
            try {
                mockWebServer.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class OverrideFillTelemetry extends QnAMaker {

        public OverrideFillTelemetry(QnAMakerEndpoint endpoint, QnAMakerOptions options, OkHttpClient httpClient,
                                     BotTelemetryClient telemetryClient, Boolean logPersonalInformation) {
            super(endpoint, options, httpClient, telemetryClient, logPersonalInformation);
        }

        @Override
        protected CompletableFuture<Void> onQnaResults(QueryResult[] queryResults, TurnContext turnContext,
                                                       Map<String, String> telemetryProperties,
                                                       Map<String, Double> telemetryMetrics) throws IOException {
            this.fillQnAEvent(queryResults, turnContext, telemetryProperties, telemetryMetrics).thenAccept(eventData -> {
                // Add my property
                eventData.getLeft().put("MyImportantProperty", "myImportantValue");

                // Log QnaMessage event
                BotTelemetryClient telemetryClient = getTelemetryClient();
                telemetryClient.trackEvent(QnATelemetryConstants.QNA_MSG_EVENT, eventData.getLeft(), eventData.getRight());

                // Create second event.
                Map<String, String> secondEventProperties = new HashMap<String, String>(){
                    {
                        put("MyImportantProperty2", "myImportantValue2");
                    }
                };
                telemetryClient.trackEvent("MySecondEvent", secondEventProperties);
            });

            return CompletableFuture.completedFuture(null);
        }
    }

    public class OverrideTelemetry extends QnAMaker {

        public OverrideTelemetry(QnAMakerEndpoint endpoint, QnAMakerOptions options, OkHttpClient httpClient,
                                 BotTelemetryClient telemetryClient, Boolean logPersonalInformation) {
            super(endpoint, options, httpClient, telemetryClient, logPersonalInformation);
        }

        @Override
        protected CompletableFuture<Void> onQnaResults(QueryResult[] queryResults, TurnContext turnContext,
                                                       Map<String, String> telemetryProperties,
                                                       Map<String, Double> telemetryMetrics) {
            Map<String, String> properties = telemetryProperties == null ? new HashMap<String, String>() : telemetryProperties;

            // GetAnswerAsync overrides derived class.
            properties.put("MyImportantProperty", "myImportantValue");

            // Log event
            BotTelemetryClient telemetryClient = getTelemetryClient();
            telemetryClient.trackEvent(QnATelemetryConstants.QNA_MSG_EVENT, properties);

            // Create second event.
            Map<String, String> secondEventProperties = new HashMap<String, String>();
            secondEventProperties.put("MyImportantProperty2", "myImportantValue2");
            telemetryClient.trackEvent("MySecondEvent", secondEventProperties);
            return CompletableFuture.completedFuture(null);
        }

    }

    private TestFlow createFlow(Dialog rootDialog, String testName) {
        Storage storage = new MemoryStorage();
        UserState userState = new UserState(storage);
        ConversationState conversationState = new ConversationState(storage);

        TestAdapter adapter = new TestAdapter(TestAdapter.createConversationReference(testName, "User1", "Bot"));
        adapter
            .useStorage(storage)
            .useBotState(userState, conversationState)
            .use(new TranscriptLoggerMiddleware(new TraceTranscriptLogger()));

        DialogManager dm = new DialogManager(rootDialog, null);
        return new TestFlow(adapter, (turnContext) -> dm.onTurn(turnContext).thenApply(task -> null));
    }

    public class QnAMakerTestDialog extends ComponentDialog implements DialogDependencies {

        public QnAMakerTestDialog(String knowledgeBaseId, String endpointKey, String hostName, OkHttpClient httpClient) {
            super("QnaMakerTestDialog");
            addDialog(new QnAMakerDialog(knowledgeBaseId, endpointKey, hostName, null,
                null, null, null, null,
                null, null, httpClient, null, null));
        }

        @Override
        public CompletableFuture<DialogTurnResult> beginDialog(DialogContext outerDc, Object options) {
            return super.beginDialog(outerDc, options);
        }

        @Override
        public CompletableFuture<DialogTurnResult> continueDialog(DialogContext dc) {
            if (dc.getContext().getActivity().getText() == "moo") {
                dc.getContext().sendActivity("Yippee ki-yay!").thenApply(task -> END_OF_TURN);
            }

            return dc.beginDialog("qnaDialog").thenApply(task -> task);
        }

        public List<Dialog> getDependencies() {
            return getDialogs().getDialogs().stream().collect(Collectors.toList());
        }

        @Override
        public CompletableFuture<DialogTurnResult> resumeDialog(DialogContext dc, DialogReason reason, Object result) {
            if((boolean) result == false) {
                dc.getContext().sendActivity("I didn't understand that.");
            }

            return super.resumeDialog(dc, reason, result).thenApply(task -> task);
        }
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
        return new QnAMaker(qnaMakerEndpoint, qnaMakerOptions, new OkHttpClient());
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

    private MockWebServer configureMockServer(MockWebServer mockWebServer,String fileName) {
        try {
            String pathToMock = this.getRequestUrl();
            String filePath = new StringBuilder("/src/test/java/com/microsoft/bot/ai/qna/testData/")
                .append(fileName)
                .toString();
            String content = getResponse(filePath);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode testData = mapper.readTree(content);
            this.initializeMockServer(mockWebServer, testData, pathToMock);
        } catch (IOException e) {
        }
        return mockWebServer;
    }

    private MockWebServer initializeMockServer(HttpStatus status) throws IOException {
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setResponseCode(status.value()));

        mockWebServer.start();
        return mockWebServer;
    }

    private HttpUrl initializeMockServer(MockWebServer mockWebServer, JsonNode response, String url) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String mockResponse = mapper.writeValueAsString(response);
        mockWebServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(mockResponse));

        mockWebServer.start();

        return mockWebServer.url(url);
    }

    private Path getFilePath(String fileName) {
        return Paths.get(System.getProperty("user.dir"), "TestData", fileName);
    }
}
