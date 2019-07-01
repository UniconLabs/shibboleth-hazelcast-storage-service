package net.unicon.iam.shibboleth.storage;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.HazelcastInstanceImpl;
import com.hazelcast.internal.serialization.impl.AbstractSerializationService;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import com.hazelcast.spi.serialization.SerializationService;
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
import java.util.concurrent.TimeUnit;

public abstract class AbstractHazelcastMapBackedStorageService extends AbstractStorageService {
    private static final Logger logger = LoggerFactory.getLogger(AbstractHazelcastMapBackedStorageService.class);
    protected final HazelcastInstance hazelcastInstance;

    public AbstractHazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;

        setupSerialization();

        this.setContextSize(Integer.MAX_VALUE);
        this.setKeySize(Integer.MAX_VALUE);
        this.setValueSize(Integer.MAX_VALUE);
    }

    protected void setupSerialization() {
        // set up the serializer for the storage record if not already configured
        SerializationService serializationService;
        if (this.hazelcastInstance instanceof HazelcastInstanceImpl) {
            serializationService = ((HazelcastInstanceImpl) this.hazelcastInstance).getSerializationService();
        } else if (this.hazelcastInstance instanceof SerializationServiceSupport) {
            serializationService = ((SerializationServiceSupport) this.hazelcastInstance).getSerializationService();
        } else {
            serializationService = null;
        }
        if (serializationService != null) {
            try {
                ((AbstractSerializationService) serializationService).register(MutableStorageRecord.class, new MutableStorageRecordSerializer());
            } catch (IllegalStateException e) {
                logger.warn("Problem registering storage record serializer", e);
            }
        }
    }

    protected long getSystemExpiration(Long expiration) {
        return (expiration == null || expiration == 0) ? 0 : expiration - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean create(@Nonnull @NotEmpty String context, @Nonnull @NotEmpty String key, @Nonnull String value, @Nullable @Positive Long expiration) throws IOException {
        IMap<Object, StorageRecord> backingMap = getMap(context, key);
        Object ikey = getKey(context, key);
        if (backingMap.containsKey(ikey)) {
            return false;
        }
        StorageRecord storageRecord = new MutableStorageRecord(value, expiration);
        if (expiration != null) {
            backingMap.put(ikey, storageRecord, getSystemExpiration(expiration), TimeUnit.MILLISECONDS);
        } else {
            backingMap.put(ikey, storageRecord);
        }
        return true;
    }

    private Pair<Long, StorageRecord> doRead(final String context, final String key, final Long version) {
        IMap backingMap = getMap(context, key);
        Object ikey = getKey(context, key);
        if (!backingMap.containsKey(ikey)) {
            return new Pair<>();
        }
        StorageRecord storageRecord = (StorageRecord) backingMap.get(ikey);
        if (version != null && version == storageRecord.getVersion()) {
            return new Pair<>(version, null);
        }
        return new Pair<>(storageRecord.getVersion(), storageRecord);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public StorageRecord read(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key) throws IOException {
        return doRead(context, key, null).getSecond();
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    public Pair<Long, StorageRecord> read(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Positive final long version) throws IOException {
        return doRead(context, key, version);
    }

    private Long doUpdate(final Long version, final String context, final String key, final String value, final Long expiration) throws IOException {
        final ILock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            MutableStorageRecord record = (MutableStorageRecord) this.doRead(context, key, null).getSecond();
            if (record == null) {
                return null;
            }

            if (version != null && version != record.getVersion()) {
                throw new VersionMismatchWrapperException(new VersionMismatchException());
            }

            if (value != null) {
                record.setValue(value);
                record.incrementVersion();
            }

            record.setExpiration(expiration);
            this.getMap(context, key).put(getKey(context, key), record, getSystemExpiration(record.getExpiration()), TimeUnit.MILLISECONDS);
            return record.getVersion();
        } finally {
            lock.unlock();
            lock.destroy();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean update(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Nonnull @NotEmpty final String value, @Nullable @Positive final Long expiration) throws IOException {
        return doUpdate(null, context, key, value, expiration) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Long updateWithVersion(@Positive long version, @Nonnull @NotEmpty String context, @Nonnull @NotEmpty String key, @Nonnull String value, @Nullable @Positive Long expiration) throws IOException, VersionMismatchException {
        try {
            return doUpdate(version, context, key, value, expiration);
        } catch (VersionMismatchWrapperException e) {
            throw (VersionMismatchException)e.getCause();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateExpiration(@Nonnull String context, @Nonnull String key, @Nullable Long expiration) throws IOException {
        return doUpdate(null, context, key, null, expiration) != null;
    }

    private boolean doDelete(Long version, String context, String key) throws IOException {
        IMap backingMap = getMap(context, key);
        Object ikey = getKey(context, key);
        if (!backingMap.containsKey(ikey)) {
            return false;
        }
        if (version != null && ((StorageRecord) backingMap.get(ikey)).getVersion() != version) {
            throw new VersionMismatchWrapperException(new VersionMismatchException());
        }
        backingMap.delete(ikey);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(@Nonnull String context, @Nonnull String key) throws IOException {
        return doDelete(null, context, key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteWithVersion(long version, @Nonnull String context, @Nonnull String key) throws IOException, VersionMismatchException {
        try {
            return doDelete(version, context, key);
        } catch (VersionMismatchWrapperException e) {
            throw (VersionMismatchException)e.getCause();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The Hazelcast implementation is a noop and is handled by hazelcast
     */
    @Override
    public void reap(@Nonnull String context) throws IOException {
    }

    @Override
    protected void doDestroy() {
        this.hazelcastInstance.shutdown();
        super.doDestroy();
    }

    protected abstract IMap<Object, StorageRecord> getMap(String context, String key);

    protected abstract Object getKey(String context, String key);

    public static class VersionMismatchWrapperException extends RuntimeException {
        public VersionMismatchWrapperException(Throwable cause) {
            super(cause);
        }
    }
}
