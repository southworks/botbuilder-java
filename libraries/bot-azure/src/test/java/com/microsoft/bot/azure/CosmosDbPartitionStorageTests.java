// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.azure;

import com.microsoft.azure.documentdb.ConnectionPolicy;
import com.microsoft.azure.documentdb.ConsistencyLevel;
import com.microsoft.azure.documentdb.Database;
import com.microsoft.azure.documentdb.DocumentClient;
import com.microsoft.azure.documentdb.DocumentClientException;
import com.microsoft.bot.builder.Storage;
import com.microsoft.bot.builder.StorageBaseTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The CosmosDB tests require the CosmosDB Emulator to be installed and running.
 *
 * More info at: https://aka.ms/documentdb-emulator-docs
 *
 * Also... Java requires the CosmosDB Emulator cert to be installed.  See "Export the SSL certificate" in
 * the link above to export the cert.  Then import the cert into the Java JDK using:
 *
 * https://docs.microsoft.com/en-us/azure/java/java-sdk-add-certificate-ca-store?view=azure-java-stable
 *
 * Note: Don't ignore the first step of "At an administrator command prompt, navigate to your JDK's jdk\jre\lib\security folder"
 */
public class CosmosDbPartitionStorageTests extends StorageBaseTests {
    private static boolean emulatorIsRunning = false;
    private static final String NO_EMULATOR_MESSAGE = "This test requires CosmosDB Emulator! go to https://aka.ms/documentdb-emulator-docs to download and install.";

    private static String CosmosServiceEndpoint = "https://localhost:8081";
    private static String CosmosAuthKey = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
    private static String CosmosDatabaseName = "test-db";
    private static String CosmosCollectionName = "bot-storage";

    private Storage storage;

    @BeforeClass
    public static void allTestsInit() throws IOException, InterruptedException, DocumentClientException {
        Process p = Runtime.getRuntime().exec
            ("cmd /C \"" + System.getenv("ProgramFiles") + "\\Azure Cosmos DB Emulator\\CosmosDB.Emulator.exe\" /GetStatus");

        int result = p.waitFor();
        if (result == 2) {
            emulatorIsRunning = true;

            DocumentClient client = new DocumentClient(
                CosmosServiceEndpoint,
                CosmosAuthKey,
                ConnectionPolicy.GetDefault(),
                ConsistencyLevel.Session);

            createDatabaseIfNeeded(client);
        }
    }

    @AfterClass
    public static void allTestCleanup() throws DocumentClientException {
        if (emulatorIsRunning) {
            DocumentClient client = new DocumentClient(
                CosmosServiceEndpoint,
                CosmosAuthKey,
                ConnectionPolicy.GetDefault(),
                ConsistencyLevel.Session);

            List<Database> databaseList = client
                .queryDatabases(
                    "SELECT * FROM root r WHERE r.id='" + CosmosDatabaseName
                        + "'", null).getQueryIterable().toList();
            if (databaseList.size() > 0) {
                client.deleteDatabase(databaseList.get(0).getSelfLink(), null);
            }
        }
    }

    @Before
    public void testInit() {
        storage = new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
            setAuthKey(CosmosAuthKey);
            setContainerId(CosmosCollectionName);
            setCosmosDbEndpoint(CosmosServiceEndpoint);
            setDatabaseId(CosmosDatabaseName);
        }});
    }

    @After
    public void testCleanup() {
        storage = null;
    }

    @Test
    public void constructorShouldThrowOnInvalidOptions() {
        try {
            new CosmosDbPartitionedStorage(null);
            Assert.fail("should have thrown for null options");
        } catch(IllegalArgumentException e) {
            // all good
        }

        try {
            new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
                setAuthKey("test");
                setContainerId("testId");
                setDatabaseId("testDb");
            }});
            Assert.fail("should have thrown for missing end point");
        } catch (IllegalArgumentException e) {

        }

        try {
            new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
                setAuthKey(null);
                setContainerId("testId");
                setDatabaseId("testDb");
                setCosmosDbEndpoint("testEndpoint");
            }});
            Assert.fail("should have thrown for missing auth key");
        } catch (IllegalArgumentException e) {

        }

        try {
            new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
                setAuthKey("testAuthKey");
                setContainerId("testId");
                setDatabaseId(null);
                setCosmosDbEndpoint("testEndpoint");
            }});
            Assert.fail("should have thrown for missing db id");
        } catch (IllegalArgumentException e) {

        }

        try {
            new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
                setAuthKey("testAuthKey");
                setContainerId(null);
                setDatabaseId("testDb");
                setCosmosDbEndpoint("testEndpoint");
            }});
            Assert.fail("should have thrown for missing collection id");
        } catch (IllegalArgumentException e) {

        }

        try {
            new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
                setAuthKey("testAuthKey");
                setContainerId("testId");
                setDatabaseId("testDb");
                setCosmosDbEndpoint("testEndpoint");
                setKeySuffix("?#*test");
                setCompatibilityMode(false);
            }});
            Assert.fail("should have thrown for invalid Row Key characters in KeySuffix");
        } catch (IllegalArgumentException e) {

        }

        try {
            new CosmosDbPartitionedStorage(new CosmosDbPartitionedStorageOptions() {{
                setAuthKey("testAuthKey");
                setContainerId("testId");
                setDatabaseId("testDb");
                setCosmosDbEndpoint("testEndpoint");
                setKeySuffix("thisisatest");
                setCompatibilityMode(true);
            }});
            Assert.fail("should have thrown for CompatibilityMode 'true' while using a KeySuffix");
        } catch (IllegalArgumentException e) {

        }
    }

    // NOTE: THESE TESTS REQUIRE THAT THE COSMOS DB EMULATOR IS INSTALLED AND STARTED !!!!!!!!!!!!!!!!!
    @Test
    public void createObjectCosmosDBPartitionTest() {
        assertEmulator();
        super.createObjectTest(storage);
    }

    // NOTE: THESE TESTS REQUIRE THAT THE COSMOS DB EMULATOR IS INSTALLED AND STARTED !!!!!!!!!!!!!!!!!
    @Test
    public void readUnknownCosmosDBPartitionTest() {
        assertEmulator();
        super.readUnknownTest(storage);
    }

    // NOTE: THESE TESTS REQUIRE THAT THE COSMOS DB EMULATOR IS INSTALLED AND STARTED !!!!!!!!!!!!!!!!!
    @Test
    public void updateObjectCosmosDBPartitionTest() {
        assertEmulator();
        super.updateObjectTest(storage);
    }

    // NOTE: THESE TESTS REQUIRE THAT THE COSMOS DB EMULATOR IS INSTALLED AND STARTED !!!!!!!!!!!!!!!!!
    @Test
    public void deleteObjectCosmosDBPartitionTest() {
        assertEmulator();
        super.deleteObjectTest(storage);
    }

    // NOTE: THESE TESTS REQUIRE THAT THE COSMOS DB EMULATOR IS INSTALLED AND STARTED !!!!!!!!!!!!!!!!!
    @Test
    public void deleteUnknownObjectCosmosDBPartitionTest() {
        assertEmulator();
        storage.delete(new String[] {"unknown_delete"});
    }

    // NOTE: THESE TESTS REQUIRE THAT THE COSMOS DB EMULATOR IS INSTALLED AND STARTED !!!!!!!!!!!!!!!!!
    @Test
    public void handleCrazyKeysCosmosDBPartition() {
        assertEmulator();
        super.handleCrazyKeys(storage);
    }

    @Test
    public void readingEmptyKeysReturnsEmptyDictionary() {
        Map<String, Object> state = storage.read(new String[] {}).join();
        Assert.assertNotNull(state);
        Assert.assertEquals(0, state.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void readingNullKeysThrowException() {
        storage.read(null).join();
    }

    @Test(expected = IllegalArgumentException.class)
    public void writingNullStoreItemsThrowException() {
        storage.write(null);
    }

    @Test
    public void writingNoStoreItemsDoesntThrow() {
        storage.write(new HashMap<>());
    }

    private static void createDatabaseIfNeeded(DocumentClient client) throws DocumentClientException {
        // Get the database if it exists
        List<Database> databaseList = client
            .queryDatabases(
                "SELECT * FROM root r WHERE r.id='" + CosmosDatabaseName
                    + "'", null).getQueryIterable().toList();

        if (databaseList.size() == 0) {
            // Create the database if it doesn't exist.
            Database databaseDefinition = new Database();
            databaseDefinition.setId(CosmosDatabaseName);

            client.createDatabase(
                databaseDefinition, null).getResource();
        }
    }

    private void assertEmulator() {
        if (!emulatorIsRunning) {
            Assert.fail(NO_EMULATOR_MESSAGE);
        }
    }
}