package net.unicon.iam.shibboleth.storage;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.PagingPredicate;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import org.opensaml.storage.MutableStorageRecord;
import org.opensaml.storage.StorageRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * Implementation of {@link org.opensaml.storage.AbstractMapBackedStorageService} that uses
 * Hazelcast for storage.
 */
public class HazelcastMapBackedStorageService extends AbstractHazelcastMapBackedStorageService {
    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance, int pageSize) {
        super(hazelcastInstance, pageSize);
    }

    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance) {
        super(hazelcastInstance);
    }

    @Override
    protected IMap<Object, StorageRecord> getMap(String context, String key) {
        return this.hazelcastInstance.getMap(context);
    }

    @Override
    protected Object getKey(String context, String key) {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateContextExpiration(@Nonnull String context, @Nullable Long expiration) throws IOException {
        IMap<String, StorageRecord> backingMap = this.hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context);
        lock.lock();
        try {
            PagingPredicate pagingPredicate = new PagingPredicate(this.pageSize);
            for (Set<Map.Entry<String, StorageRecord>> entrySet = backingMap.entrySet(pagingPredicate);
                 !entrySet.isEmpty();
                 pagingPredicate.nextPage(), entrySet = backingMap.entrySet(pagingPredicate)) {
                for (Map.Entry entry : entrySet) {
                    this.updateExpiration(context, (String) entry.getKey(), expiration);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteContext(@Nonnull @NotEmpty String context) throws IOException {
        this.hazelcastInstance.getMap(context).clear();
    }
}
