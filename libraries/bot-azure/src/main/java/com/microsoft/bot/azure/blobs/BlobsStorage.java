// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.azure.blobs;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.microsoft.bot.builder.Storage;
import com.microsoft.bot.builder.StoreItem;
import com.microsoft.bot.restclient.serializer.JacksonAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BlobsStorage implements Storage {
    private final JacksonAdapter jacksonAdapter = new JacksonAdapter();
    private final BlobContainerClient containerClient;

    public BlobsStorage(String dataConnectionString, String containerName) {
        if (StringUtils.isBlank(dataConnectionString)) {
            throw new IllegalArgumentException("dataConnectionString is required.");
        }

        if (StringUtils.isBlank(containerName)) {
            throw new IllegalArgumentException("containerName is required.");
        }

        containerClient = new BlobContainerClientBuilder()
                            .connectionString(dataConnectionString)
                            .containerName(containerName)
                            .buildClient();
    }

    @Override
    public CompletableFuture<Void> delete(String[] keys) {
        if (keys == null) {
            throw new IllegalArgumentException("The 'keys' parameter is required.");
        }

        for (String key : keys) {
            String blobName = getBlobName(key);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            try {
                blobClient.delete();
            } catch (BlobStorageException e) {
                if (e.getErrorCode().equals(BlobErrorCode.CONTAINER_NOT_FOUND)) {
                    throw new RuntimeException(e);
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Object>> read(String[] keys) {
        if (keys == null) {
            throw new IllegalArgumentException("The 'keys' parameter is required.");
        }

        if (!containerClient.exists()) {
            try {
                containerClient.create();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Map<String, Object> items = new HashMap<>();

        for (String key : keys) {
            try {
                String blobName = getBlobName(key);
                BlobClient blobClient = containerClient.getBlobClient(blobName);
                innerReadBlob(blobClient).thenAccept(value -> items.put(key, value));
            } catch (HttpResponseException e) {
                if (e.getResponse().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                    continue;
                }
            }
        }
        return CompletableFuture.completedFuture(items);
    }

    public CompletableFuture<Void> write(Map<String, Object> changes) {
        if (changes == null) {
            throw new IllegalArgumentException("The 'changes' parameter is required.");
        }

        if (!containerClient.exists()) {
            try {
                containerClient.create();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Map.Entry<String, Object> keyValuePair : changes.entrySet()) {
            Object newValue = keyValuePair.getValue();
            StoreItem storeItem = newValue instanceof StoreItem ? (StoreItem) newValue : null;

            // "*" eTag in StoreItem converts to null condition for AccessCondition
            boolean isNullOrEmpty = storeItem == null || StringUtils.isBlank(storeItem.getETag()) || storeItem.getETag().equals("*");
            BlobRequestConditions accessCondition = !isNullOrEmpty
                ? new BlobRequestConditions().setIfMatch(storeItem.getETag())
                : null;

            String blobName = getBlobName(keyValuePair.getKey());
            BlobClient blobReference = containerClient.getBlobClient(blobName);

            try {
                String json = jacksonAdapter.serialize(newValue);
                InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                //verify the corresponding length
                blobReference.uploadWithResponse(stream, stream.available(), null, null, null, null, accessCondition, null, Context.NONE);
            } catch(HttpResponseException e) {
                if (e.getResponse().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                    StringBuilder sb = new StringBuilder("An error occurred while trying to write an object. The underlying ");
                    sb.append(BlobErrorCode.INVALID_BLOCK_LIST);
                    sb.append(" error is commonly caused due to concurrently uploading an object larger than 128MB in size.");

                    throw new HttpResponseException(sb.toString(), e.getResponse());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private static String getBlobName(String key)
    {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("key");
        }

        String blobName;
        try {
            blobName = URLEncoder.encode(key, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("The key could not be encoded");
        }

        return blobName;
    }

    private CompletableFuture<Object> innerReadBlob(BlobClient blobReference) {
        Integer i = 0;
        while (true) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                blobReference.download(outputStream);
                String contentString  = outputStream.toString();
                Object obj = jacksonAdapter.deserialize(contentString , Object.class);

                if (obj instanceof StoreItem) {
                    String eTag = blobReference.getProperties().getETag();
                    ((StoreItem) obj).setETag(eTag);
                }

                return CompletableFuture.completedFuture(obj);
            } catch (HttpResponseException e) {
                if (e.getResponse().getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED) {
                    // additional retry logic, even though this is a read operation blob storage can return 412 if there is contention
                    if (i++ < 8) {
                        try {
                            Thread.sleep(2000);
                            continue;
                        } catch (InterruptedException ex) {
                            break;
                        }
                    }
                    throw e;
                } else {
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
