package net.unicon.iam.shibboleth.storage.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicates;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
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
    private static final int DEFAULT_PAGE_SIZE = 100;
    protected final int pageSize;

    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance, int pageSize) {
        super(hazelcastInstance);
        this.pageSize = pageSize;
    }

    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, DEFAULT_PAGE_SIZE);
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
        final Lock lock = hazelcastInstance.getCPSubsystem().getLock(context);
        lock.lock();
        try {
            PagingPredicate pagingPredicate = Predicates.pagingPredicate(this.pageSize);
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