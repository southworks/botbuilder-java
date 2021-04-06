// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.sample.scaleout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobRequestOptions;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.bot.schema.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of the ETag aware IStore interface against Azure Blob Storage.
 */
public class BlobStore implements Store {

    private final CloudBlobContainer container;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public BlobStore(String accountName, String accountKey, String containerName) throws URISyntaxException, StorageException {
        if (StringUtils.isBlank(accountName)) {
            throw new IllegalArgumentException("accountName cannot be null");
        }
        if (StringUtils.isBlank(accountKey)) {
            throw new IllegalArgumentException("accountKey cannot be null");
        }
        if (StringUtils.isBlank(containerName)) {
            throw new IllegalArgumentException("containerName cannot be null");
        }

        // Create storage credential from name and key
        StorageCredentials storageCredentials = new StorageCredentialsAccountAndKey(accountName, accountKey);
        // Create storage account
        CloudStorageAccount cloudStorageAccount = new CloudStorageAccount(storageCredentials, true);
        CloudBlobClient client = cloudStorageAccount.createCloudBlobClient();
        container = client.getContainerReference(containerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Pair<JsonNode, String>> load(String key) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("key cannot be null");
        }
        try {
            CloudBlockBlob blob = container.getBlockBlobReference(key);
            String content = blob.downloadText();
            JsonNode obj = objectMapper.readTree(content);
            String etag = blob.getProperties().getEtag();
            return CompletableFuture.completedFuture(new Pair<>(obj, etag));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            if (e.getHttpStatusCode() == HttpStatus.NOT_FOUND.value()) {
                return CompletableFuture.completedFuture(new Pair<>(objectMapper.nullNode(), null));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return CompletableFuture.completedFuture(new Pair<>(null, null));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Boolean> save(String key, JsonNode obj, String etag) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("key cannot be null");
        }

        if (obj == null) {
            throw new IllegalArgumentException("obj cannot be null");
        }

        try {
            CloudBlockBlob blob = container.getBlockBlobReference(key);
            blob.getProperties().setContentType("application/json");
            String content = obj.toString();
            if (etag != null) {
                AccessCondition accessCondition = new AccessCondition();
                accessCondition.setIfMatch(etag);
                blob.uploadText(
                    content,
                    StandardCharsets.UTF_8.name(),
                    accessCondition,
                    new BlobRequestOptions(),
                    new OperationContext());
            } else {
                blob.uploadText(content);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (StorageException e) {
            if (e.getHttpStatusCode() == HttpStatus.PRECONDITION_FAILED.value()) {
                return CompletableFuture.completedFuture(false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            return CompletableFuture.completedFuture(true);
        }
    }
}
