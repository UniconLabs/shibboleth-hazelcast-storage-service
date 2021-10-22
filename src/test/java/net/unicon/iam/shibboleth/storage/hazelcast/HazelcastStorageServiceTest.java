package net.unicon.iam.shibboleth.storage.hazelcast;

import org.opensaml.storage.StorageRecord;
import org.opensaml.storage.testing.StorageServiceTest;
import org.testng.annotations.Test;

import java.io.IOException;

public abstract class HazelcastStorageServiceTest extends StorageServiceTest {

    @Test
    public void testUpdateContextExpiration() throws IOException {
        String context = "testContext";

        for (int i = 0; i < 10; i++) {
            this.shared.create(context, Integer.toString(i), Integer.toString(i), System.currentTimeMillis() + 500000);
        }

        Long newExpiration = System.currentTimeMillis() + 1000000;
        this.shared.updateContextExpiration(context, newExpiration);

        for (int i = 0; i < 10; i++) {
            StorageRecord record = this.shared.read(context, Integer.toString(i));
            assert record.getExpiration().equals(newExpiration);
        }
    }
}