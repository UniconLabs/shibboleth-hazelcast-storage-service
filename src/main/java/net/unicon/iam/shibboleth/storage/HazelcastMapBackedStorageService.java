package net.unicon.iam.shibboleth.storage;

import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.HazelcastInstanceImpl;
import com.hazelcast.nio.serialization.SerializationService;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import net.shibboleth.utilities.java.support.annotation.constraint.Positive;
import net.shibboleth.utilities.java.support.collection.Pair;
import org.opensaml.storage.AbstractStorageService;
import org.opensaml.storage.MutableStorageRecord;
import org.opensaml.storage.StorageRecord;
import org.opensaml.storage.VersionMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Implementation of {@link org.opensaml.storage.AbstractMapBackedStorageService} that uses
 * Hazelcast for storage.
 */
public class HazelcastMapBackedStorageService extends AbstractStorageService {
    private static final Logger logger = LoggerFactory.getLogger(HazelcastMapBackedStorageService.class);

    private final HazelcastInstance hazelcastInstance;
    private static final int DEFAULT_PAGE_SIZE = 100;
    private final int pageSize;

    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance, int pageSize) {
        this.hazelcastInstance = hazelcastInstance;
        this.pageSize = pageSize;

        setupSerialization();

        this.setContextSize(Integer.MAX_VALUE);
        this.setKeySize(Integer.MAX_VALUE);
        this.setValueSize(Integer.MAX_VALUE);
    }

    private void setupSerialization() {
        // set up the serializer for the storage record if not already configured
        SerializationService serializationService;
        if (this.hazelcastInstance instanceof HazelcastInstanceImpl) {
            serializationService = ((HazelcastInstanceImpl)this.hazelcastInstance).getSerializationService();
        } else if (this.hazelcastInstance instanceof SerializationServiceSupport) {
            serializationService = ((SerializationServiceSupport)this.hazelcastInstance).getSerializationService();
        } else {
            serializationService = null;
        }
        if (serializationService != null) {
            try {
                serializationService.register(MutableStorageRecord.class, new MutableStorageRecordSerializer());
            } catch (IllegalStateException e) {
                logger.warn("Problem registering storage record serializer", e);
            }
        }
    }

    public HazelcastMapBackedStorageService() {
        this(Hazelcast.newHazelcastInstance(getConfig()), DEFAULT_PAGE_SIZE);
    }

    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, DEFAULT_PAGE_SIZE);
    }

    public HazelcastMapBackedStorageService(int pageSize) {
        this(Hazelcast.newHazelcastInstance(getConfig()), pageSize);
    }

    private static Config getConfig() {
        Config config = new Config();

        SerializerConfig serializerConfig = new SerializerConfig().setImplementation(new MutableStorageRecordSerializer()).setTypeClass(MutableStorageRecord.class);
        config.getSerializationConfig().addSerializerConfig(serializerConfig);

        return config;
    }

    @Override
    protected void doDestroy() {
        this.hazelcastInstance.shutdown();
        super.doDestroy();
    }

    private long getSystemExpiration(Long expiration) {
        return expiration == 0 ? 0 : expiration - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean create(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Nonnull @NotEmpty final String value, @Nullable @Positive final Long expiration) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        if (backingMap.containsKey(key)) {
            return false;
        }
        StorageRecord storageRecord = new MutableStorageRecord(value, expiration);
        if (expiration != null) {
            backingMap.put(key, storageRecord, getSystemExpiration(expiration), TimeUnit.MILLISECONDS);
        } else {
            backingMap.put(key, storageRecord);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public StorageRecord read(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key) throws IOException {
        IMap backingMap = hazelcastInstance.getMap(context);
        StorageRecord storageRecord = (StorageRecord) backingMap.get(key);
        return storageRecord;
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Pair<Long, StorageRecord> read(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Positive final long version) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        StorageRecord storageRecord = backingMap.get(key);
        if (version == storageRecord.getVersion()) {
            return new Pair<Long, StorageRecord>(version, null);
        }
        return new Pair<Long, StorageRecord>(version, storageRecord);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean update(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Nonnull @NotEmpty final String value, @Nullable @Positive final Long expiration) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            if (!backingMap.containsKey(key)) {
                return false;
            }
            MutableStorageRecord record = (MutableStorageRecord) backingMap.get(key);

            record.setValue(value);
            record.incrementVersion();

            record.setExpiration(expiration);
            backingMap.put(key, record, getSystemExpiration(record.getExpiration()), TimeUnit.MILLISECONDS);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Long updateWithVersion(@Positive long version, @Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Nonnull @NotEmpty final String value, @Nullable @Positive final Long expiration) throws IOException, VersionMismatchException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            if (!backingMap.containsKey(key)) {
                return null;
            }
            MutableStorageRecord record = (MutableStorageRecord) backingMap.get(key);
            if (version != record.getVersion()) {
                throw new VersionMismatchException();
            }

            record.setValue(value);
            record.incrementVersion();

            record.setExpiration(expiration);
            backingMap.put(key, record, getSystemExpiration(record.getExpiration()), TimeUnit.MILLISECONDS);
            return record.getVersion();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateExpiration(@Nonnull String context, @Nonnull String key, @Nullable Long expiration) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            if (!backingMap.containsKey(key)) {
                return false;
            }
            MutableStorageRecord record = (MutableStorageRecord) backingMap.get(key);
            record.setExpiration(expiration);
            backingMap.put(key, record, getSystemExpiration(record.getExpiration()), TimeUnit.MILLISECONDS);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(@Nonnull String context, @Nonnull String key) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        if (!backingMap.containsKey(key)) {
            return false;
        }
        backingMap.delete(key);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteWithVersion(long version, @Nonnull String context, @Nonnull String key) throws IOException, VersionMismatchException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        if (!backingMap.containsKey(key)) {
            return false;
        }
        StorageRecord record = backingMap.get(key);
        if (version != record.getVersion()) {
            throw new VersionMismatchException();
        }
        backingMap.delete(key);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * The Hazelcast implementation is a noop and is handled by hazelcast
     */
    @Override
    public void reap(@Nonnull String context) throws IOException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateContextExpiration(@Nonnull String context, @Nullable Long expiration) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context);
        lock.lock();
        try {
            PagingPredicate pagingPredicate = new PagingPredicate(this.pageSize);
            for (Set<Map.Entry<String, StorageRecord>> entrySet = backingMap.entrySet(pagingPredicate);
                 !entrySet.isEmpty();
                 pagingPredicate.nextPage(), entrySet = backingMap.entrySet()) {
                for (Map.Entry entry : entrySet) {
                    ((MutableStorageRecord) entry.getValue()).setExpiration(getSystemExpiration(expiration));
                    backingMap.set((String) entry.getKey(), (MutableStorageRecord) entry.getValue());
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
    public void deleteContext(@Nonnull String context) throws IOException {
        hazelcastInstance.getMap(context).clear();
    }

    @Nullable
    @Override
    protected TimerTask getCleanupTask() {
        return new TimerTask() {
            @Override
            public void run() {
                // does nothing
            }
        };
    }

    public static class VersionMutableStorageRecord extends MutableStorageRecord {
        public VersionMutableStorageRecord(String value, Long expiration, Long version) {
            super(value, expiration);
            super.setVersion(version);
        }
    }
}
