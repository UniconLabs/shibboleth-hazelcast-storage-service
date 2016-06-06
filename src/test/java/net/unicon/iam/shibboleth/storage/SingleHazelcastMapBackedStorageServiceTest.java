package net.unicon.iam.shibboleth.storage;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import org.opensaml.storage.StorageService;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import javax.annotation.Nonnull;

public class SingleHazelcastMapBackedStorageServiceTest extends HazelcastStorageServiceTest {
    private SingleHazelcastMapBackedStorageService singleHazelcastMapBackedStorageService;
    private HazelcastInstance hazelcastInstance;

    @BeforeClass
    @Override
    protected void setUp() throws ComponentInitializationException {
        this.hazelcastInstance = Hazelcast.newHazelcastInstance();
        this.singleHazelcastMapBackedStorageService = new SingleHazelcastMapBackedStorageService("test", this.hazelcastInstance);
        this.singleHazelcastMapBackedStorageService.setId("test");
        super.setUp();
    }

    @AfterClass
    @Override
    protected void tearDown() {
        this.hazelcastInstance.shutdown();
        super.tearDown();
    }

    @Nonnull
    @Override
    protected StorageService getStorageService() {
        return singleHazelcastMapBackedStorageService;
    }
}
