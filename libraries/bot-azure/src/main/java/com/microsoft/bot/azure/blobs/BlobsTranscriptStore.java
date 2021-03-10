// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.azure.blobs;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.rest.PagedResponse;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.microsoft.bot.builder.BotAssert;
import com.microsoft.bot.builder.PagedResult;
import com.microsoft.bot.builder.TranscriptInfo;
import com.microsoft.bot.builder.TranscriptStore;
import com.microsoft.bot.restclient.serializer.JacksonAdapter;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.ActivityTypes;
import com.microsoft.bot.schema.ChannelAccount;
import com.microsoft.bot.schema.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The blobs transcript store stores transcripts in an Azure Blob container.
 * Each activity is stored as json blob in structure of
 * container/{channelId]/{conversationId}/{Timestamp.ticks}-{activity.id}.json.
 */
public class BlobsTranscriptStore implements TranscriptStore {

    // Containers checked for creation.
    private static HashSet<String> checkedContainers = new HashSet<String>();

    private final Integer milisecondsTimeout = 2000;
    private final Integer retryTimes = 3;

    private final JacksonAdapter jacksonAdapter = new JacksonAdapter();;
    private BlobContainerClient containerClient;

    /**
     * Initializes a new instance of the {@link BlobsTranscriptStore} class.
     * @param dataConnectionString Azure Storage connection string.
     * @param containerName Name of the Blob container where entities will be stored.
     */
    public BlobsTranscriptStore(String dataConnectionString, String containerName) {
        if (StringUtils.isBlank(dataConnectionString)) {
            throw new IllegalArgumentException("dataConnectionString");
        }

        if (StringUtils.isBlank(containerName)) {
            throw new IllegalArgumentException("containerName");
        }

        // Triggers a check for the existence of the container
        containerClient = this.getContainerClient(dataConnectionString, containerName);
    }

    private BlobContainerClient getContainerClient(String dataConnectionString, String containerName) {
        containerName = containerName.toLowerCase();
        containerClient = new BlobContainerClientBuilder()
            .connectionString(dataConnectionString)
            .containerName(containerName)
            .buildClient();
        if (!checkedContainers.contains(containerName)) {
            checkedContainers.add(containerName);
            if (!containerClient.exists()) {
                try {
                    containerClient.create();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return containerClient;
    }

    /**
     * Log an activity to the transcript.
     * @param activity Activity being logged.
     * @return A CompletableFuture that represents the work queued to execute.
     */
    public CompletableFuture<Void> logActivity(Activity activity) {
        BotAssert.activityNotNull(activity);

        switch (activity.getType()) {
            case ActivityTypes.MESSAGE_UPDATE:
                innerReadBlob(activity).thenAccept(activityAndBlob -> {
                    if (activityAndBlob.getLeft() != null) {
                        Activity updateActivity = null;
                        try {
                            updateActivity = jacksonAdapter
                                .deserialize(jacksonAdapter.serialize(activity), Activity.class);
                        } catch (IOException exception) {
                            exception.printStackTrace();
                        }
                        updateActivity.setType(ActivityTypes.MESSAGE);
                        updateActivity.setLocalTimestamp(activityAndBlob.getLeft().getLocalTimestamp());
                        updateActivity.setTimestamp(activityAndBlob.getLeft().getTimestamp());
                        logActivityToBlobClient(updateActivity, activityAndBlob.getRight(), true)
                            .thenApply(task -> CompletableFuture.completedFuture(null));
                    }
                });

                return CompletableFuture.completedFuture(null);
            case ActivityTypes.MESSAGE_DELETE:
                innerReadBlob(activity).thenAccept(activityAndBlob -> {
                   if (activityAndBlob.getLeft() != null) {
                       ChannelAccount from = new ChannelAccount() {
                           {
                               setId("deleted");
                               setRole(activityAndBlob.getLeft().getFrom().getRole());
                           }
                       };
                       ChannelAccount recipient = new ChannelAccount() {
                           {
                               setId("deleted");
                               setRole(activityAndBlob.getLeft().getRecipient().getRole());
                           }
                       };
                       // tombstone the original message
                       Activity tombstonedActivity = new Activity() {
                           {
                               setType(ActivityTypes.MESSAGE_DELETE);
                               setId(activityAndBlob.getLeft().getId());
                               setFrom(from);
                               setRecipient(recipient);
                               setLocale(activityAndBlob.getLeft().getLocale());
                               setLocalTimestamp(activityAndBlob.getLeft().getTimestamp());
                               setTimestamp(activityAndBlob.getLeft().getTimestamp());
                               setChannelId(activityAndBlob.getLeft().getChannelId());
                               setConversation(activityAndBlob.getLeft().getConversation());
                               setServiceUrl(activityAndBlob.getLeft().getServiceUrl());
                               setReplyToId(activityAndBlob.getLeft().getReplyToId());
                           }
                       };

                       logActivityToBlobClient(tombstonedActivity, activityAndBlob.getRight(), true)
                           .thenAcceptAsync(task -> CompletableFuture.completedFuture(null));
                   }
                });

                return CompletableFuture.completedFuture(null);
            default:
                String blobName = this.getBlobName(activity);
                BlobClient blobClient = containerClient.getBlobClient(blobName);
                logActivityToBlobClient(activity, blobClient, true).
                    thenApply(task -> CompletableFuture.completedFuture(null));
                return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Get activities for a conversation (Aka the transcript).
     * @param channelId         The ID of the channel the conversation is in.
     * @param conversationId    The ID of the conversation.
     * @param continuationToken The continuation token (if available).
     * @param startDate         A cutoff date. Activities older than this date are
     *                          not included.
     * @return PagedResult of activities.
     */
    public CompletableFuture<PagedResult<Activity>> getTranscriptActivities(String channelId, String conversationId,
                                                                            @Nullable String continuationToken,
                                                                            OffsetDateTime startDate) {
        if (startDate == null) {
            startDate = OffsetDateTime.now(ZoneId.of("UTC"));
        }

        final int pageSize = 20;

        if (StringUtils.isBlank(channelId)) {
            throw new IllegalArgumentException("Missing channelId");
        }

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("Missing conversationId");
        }

        PagedResult<Activity> pagedResult = new PagedResult<Activity>();

        String token = null;
        List<BlobItem> blobs = new ArrayList<BlobItem>();
        do {
            String prefix = String.format("%s/%s/", sanitizeKey(channelId), sanitizeKey(conversationId));
            Iterable<PagedResponse<BlobItem>> resultSegment = containerClient.listBlobsByHierarchy(prefix)
                .iterableByPage(token);
            token = null;

            for (PagedResponse<BlobItem> blobPage: resultSegment) {
                for (BlobItem blobItem: blobPage.getValue()) {
                    OffsetDateTime parseDateTime = OffsetDateTime.parse(blobItem.getMetadata().get("Timestamp"));
                    if (parseDateTime.isAfter(startDate)
                        || parseDateTime.isEqual(startDate)) {
                        if (continuationToken != null) {
                            if (blobItem.getName().equals(continuationToken)) {
                                // we found continuation token
                                continuationToken = null;
                            }
                        } else {
                            blobs.add(blobItem);
                            if (blobs.size() == pageSize) {
                                break;
                            }
                        }
                    }
                }

                // Get the continuation token and loop until it is empty.
                token = blobPage.getContinuationToken();
            }
        } while (!StringUtils.isBlank(token) && blobs.size() < pageSize);

        pagedResult.setItems(blobs
            .stream()
            .map(bl -> {
                BlobClient blobClient = containerClient.getBlobClient(bl.getName());
                return this.getActivityFromBlobClient(blobClient);
            })
            .map(t -> t.join()).collect(Collectors.toList()));

        if (pagedResult.getItems().size() == pageSize) {
            if (!blobs.isEmpty() && blobs.get(blobs.size() - 1) != null) {
                pagedResult.setContinuationToken(blobs.get(blobs.size() - 1).getName());
            }
        }

        return CompletableFuture.completedFuture(pagedResult);
    }

    /**
     * List conversations in the channelId.
     * @param channelId         The ID of the channel.
     * @param continuationToken The continuation token (if available).
     * @return A CompletableFuture that represents the work queued to execute.
     */
    public CompletableFuture<PagedResult<TranscriptInfo>> listTranscripts(String channelId,
                                                                          @Nullable String continuationToken) {
        final int pageSize = 20;

        if (StringUtils.isBlank(channelId)) {
            throw new IllegalArgumentException("Missing channelId");
        }

        String token = null;

        List<TranscriptInfo> conversations = new ArrayList<TranscriptInfo>();
        do {
            String prefix = String.format("%s/", sanitizeKey(channelId));
            Iterable<PagedResponse<BlobItem>> resultSegment = containerClient.listBlobsByHierarchy(prefix)
                .iterableByPage(token);
            token = null;
            for (PagedResponse<BlobItem> blobPage: resultSegment) {
                for (BlobItem blobItem: blobPage.getValue()) {
                    // Unescape the Id we escaped when we saved it
                    String conversationId = new String();
                    String[] splitName = blobItem.getName().split("/");
                    if (splitName.length > 0 && splitName[splitName.length - 1].length() > 0) {
                        conversationId = splitName[splitName.length - 1];
                    }
                    try {
                        conversationId = URLDecoder.decode(conversationId, StandardCharsets.UTF_8.name());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    TranscriptInfo conversation =
                        new TranscriptInfo(conversationId, channelId, blobItem.getProperties().getCreationTime());
                    if (continuationToken != null) {
                        if (StringUtils.equals(conversation.getId(), continuationToken)) {
                            // we found continuation token
                            continuationToken = null;
                        }

                        // skip record
                    } else {
                        conversations.add(conversation);
                        if (conversations.size() == pageSize) {
                            break;
                        }
                    }
                }
            }
        } while (!StringUtils.isBlank(token) && conversations.size() < pageSize);

        PagedResult<TranscriptInfo> pagedResult = new PagedResult<TranscriptInfo>() {
            {
                setItems(conversations);
            }
        };

        if (pagedResult.getItems().size() == pageSize) {
            if (!pagedResult.getItems().isEmpty()
                && pagedResult.getItems().get(pagedResult.getItems().size() - 1) != null) {
                pagedResult.setContinuationToken(pagedResult.getItems().get(pagedResult.getItems().size() - 1).getId());
            }
        }

        return CompletableFuture.completedFuture(pagedResult);
    }

    /**
     * Delete a specific conversation and all of it's activities.
     * @param channelId      The ID of the channel the conversation is in.
     * @param conversationId The ID of the conversation to delete.
     * @return A CompletableFuture that represents the work queued to execute.
     */
    public CompletableFuture<Void> deleteTranscript(String channelId, String conversationId) {
        if (StringUtils.isBlank(channelId)) {
            throw new IllegalArgumentException("Missing channelId");
        }

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("Missing conversationId");
        }

        String token = null;
        do {
            String prefix = String.format("%s/%s/", sanitizeKey(channelId), sanitizeKey(conversationId));
            Iterable<PagedResponse<BlobItem>> resultSegment = containerClient
                .listBlobsByHierarchy(prefix).iterableByPage(token);
            token = null;
            for (PagedResponse<BlobItem> blobPage: resultSegment) {
                for (BlobItem blobItem : blobPage.getValue()) {
                    BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
                    if (blobClient.exists()) {
                        try {
                            blobClient.delete();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                    // Get the continuation token and loop until it is empty.
                    token = blobPage.getContinuationToken();
                }
            }
        } while (!StringUtils.isBlank(token));

        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Pair<Activity, BlobClient>> innerReadBlob(Activity activity) {
        int i = 0;
        while (true) {
            try {
                String token = null;
                do {
                    String prefix = String.format("%s/%s/",
                        sanitizeKey(activity.getChannelId()), sanitizeKey(activity.getConversation().getId()));
                    Iterable<PagedResponse<BlobItem>> resultSegment = containerClient
                        .listBlobsByHierarchy(prefix).iterableByPage(token);
                    token = null;
                    for (PagedResponse<BlobItem> blobPage: resultSegment) {
                        for (BlobItem blobItem : blobPage.getValue()) {
                            if (blobItem.getMetadata().get("id").equals(activity.getId())) {
                                BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
                                Activity blobActivity = this.getActivityFromBlobClient(blobClient).join();
                                return CompletableFuture
                                    .completedFuture(new Pair<Activity, BlobClient>(blobActivity, blobClient));
                            }
                        }
                    }
                } while (!StringUtils.isBlank(token));
            } catch (HttpResponseException ex) {
                if (ex.getResponse().getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED) {
                    // additional retry logic,
                    // even though this is a read operation blob storage can return 412 if there is contention
                    if (i < retryTimes) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(milisecondsTimeout);
                            i++;
                            continue;
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    throw ex;
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Activity> getActivityFromBlobClient(BlobClient blobClient) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        blobClient.download(content);
        String contentString = new String(content.toByteArray());
        try {
            return jacksonAdapter.deserialize(contentString, Activity.class);
        } catch (IOException exception) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> logActivityToBlobClient(Activity activity, BlobClient blobClient,
                                                            Boolean overwrite) {
        if (overwrite == null) {
            overwrite = false;
        }
        String activityJson = null;
        try {
            activityJson = jacksonAdapter.serialize(activity);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        InputStream data = new ByteArrayInputStream(activityJson.getBytes(StandardCharsets.UTF_8));

        try {
            blobClient.upload(data, data.available(), overwrite);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        Map<String, String> metaData = new HashMap<String, String>();
        metaData.put("Id", activity.getId());
        if (activity.getFrom() != null) {
            metaData.put("FromId", activity.getFrom().getId());
        }

        if (activity.getRecipient() != null) {
            metaData.put("RecipientId", activity.getRecipient().getId());
        }
        metaData.put("Timestamp", activity.getTimestamp().toString());

        blobClient.setMetadata(metaData);

        return CompletableFuture.completedFuture(null);
    }

    private String getBlobName(Activity activity) {
        String blobName = String.format("%1$s/%2$s/%3$s-%4$s.json",
            sanitizeKey(activity.getChannelId()), activity.getConversation().getId(),
            this.formatTicks(activity.getTimestamp()), sanitizeKey(activity.getId()));

        return blobName;
    }

    private String sanitizeKey(String key) {
        // Blob Name rules: case-sensitive any url char
        try {
            return URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    /**
     * Formats a timestamp in a way that is consistent with the C# SDK.
     * @param dateTime The dateTime used to get the ticks
     * @return The String representing the ticks.
     */
    private String formatTicks(OffsetDateTime dateTime) {
        final long epochTicks = 621355968000000000L; // the number of .net ticks at the unix epoch
        final int ticksPerMillisecond = 10000;
        final long ticks = epochTicks + dateTime.toInstant().toEpochMilli() * ticksPerMillisecond;
        return String.valueOf(ticks);
    }
}
