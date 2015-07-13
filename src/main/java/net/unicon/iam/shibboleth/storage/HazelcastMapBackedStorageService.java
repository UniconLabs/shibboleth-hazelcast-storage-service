package net.unicon.iam.shibboleth.storage;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import net.shibboleth.utilities.java.support.collection.Pair;
import org.opensaml.storage.AbstractStorageService;
import org.opensaml.storage.MutableStorageRecord;
import org.opensaml.storage.StorageRecord;
import org.opensaml.storage.VersionMismatchException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Implementation of {@link org.opensaml.storage.AbstractMapBackedStorageService} that uses
 * Hazelcast for storage.
 */
public class HazelcastMapBackedStorageService extends AbstractStorageService {
    private final HazelcastInstance hazelcastInstance;

    public HazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;

        this.setContextSize(Integer.MAX_VALUE);
        this.setKeySize(Integer.MAX_VALUE);
        this.setValueSize(Integer.MAX_VALUE);
    }

    public HazelcastMapBackedStorageService() {
        this(Hazelcast.newHazelcastInstance());
    }

    private long getSystemExpiration(Long expiration) {
        return expiration == 0 ? 0 : expiration - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean create(String context, String key, String value, Long expiration) throws IOException {
        IMap backingMap = hazelcastInstance.getMap(context);
        if (backingMap.containsKey(key)) {
            return false;
        }
        StorageRecord storageRecord = new SerializableMutableStorageRecord(value, expiration);
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
    public StorageRecord read(String context, String key) throws IOException {
        IMap backingMap = hazelcastInstance.getMap(context);
        return (StorageRecord) backingMap.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Pair<Long, StorageRecord> read(String context, String key, long version) throws IOException {
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
    public boolean update(@Nonnull String context, @Nonnull String key, @Nonnull String value, @Nullable Long expiration) throws IOException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            if (!backingMap.containsKey(key)) {
                return false;
            }
            SerializableMutableStorageRecord record = (SerializableMutableStorageRecord) backingMap.get(key);
            if (value != null) {
                record.setValue(value);
                record.incrementVersion();
            }
            record.setExpiration(getSystemExpiration(expiration));
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
    public Long updateWithVersion(long version, @Nonnull String context, @Nonnull String key, @Nonnull String value, @Nullable Long expiration) throws IOException, VersionMismatchException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            if (!backingMap.containsKey(key)) {
                return null;
            }
            SerializableMutableStorageRecord record = (SerializableMutableStorageRecord) backingMap.get(key);
            if (version != record.getVersion()) {
                throw new VersionMismatchException();
            }
            if (value != null) {
                record.setValue(value);
                record.incrementVersion();
            }
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
            SerializableMutableStorageRecord record = (SerializableMutableStorageRecord) backingMap.get(key);
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
            for (Map.Entry entry: backingMap.entrySet()) {
                ((SerializableMutableStorageRecord)entry.getValue()).setExpiration(getSystemExpiration(expiration));
                backingMap.set((String)entry.getKey(), (SerializableMutableStorageRecord)entry.getValue());
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

    public static class SerializableMutableStorageRecord extends MutableStorageRecord implements Serializable {
        public SerializableMutableStorageRecord(@Nonnull String val, @Nullable Long exp) {
            super(val, exp);
        }
    }
}
