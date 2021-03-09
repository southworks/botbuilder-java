package com.microsoft.bot.azure;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.microsoft.bot.builder.TranscriptStore;
import com.microsoft.bot.schema.Activity;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.IOException;

//TODO this class need TranscriptStoreBaseTests and BlobsTranscriptStore
public class BlobsTranscriptStoreTests extends TranscriptStoreBaseTests {

    private static Boolean EMULATOR_IS_RUNNING = false;

    @Rule
    private static final TestName testName = new TestName();

    @Override
    protected static String containerName = String.format("blobstranscript%s", testName.getMethodName());

    @Override
    protected TranscriptStore transcriptStore = new BlobsTranscriptStore(blobStorageEmulatorConnectionString, containerName);

    private static BlobContainerClient testBlobClient;

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
                Activity a = this.createActivity(0 ,0 , longId);

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

}
