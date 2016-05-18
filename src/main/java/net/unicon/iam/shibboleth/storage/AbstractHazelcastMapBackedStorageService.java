package net.unicon.iam.shibboleth.storage;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.instance.HazelcastInstanceImpl;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.AbstractSerializationService;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import net.shibboleth.utilities.java.support.annotation.constraint.Positive;
import net.shibboleth.utilities.java.support.collection.Pair;
import org.opensaml.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public abstract class AbstractHazelcastMapBackedStorageService extends AbstractStorageService {
    private static final Logger logger = LoggerFactory.getLogger(AbstractHazelcastMapBackedStorageService.class);
    protected final HazelcastInstance hazelcastInstance;

    private static final int DEFAULT_PAGE_SIZE = 100;
    protected final int pageSize;

    public AbstractHazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance, int pageSize) {
        this.hazelcastInstance = hazelcastInstance;
        this.pageSize = pageSize;

        setupSerialization();

        this.setContextSize(Integer.MAX_VALUE);
        this.setKeySize(Integer.MAX_VALUE);
        this.setValueSize(Integer.MAX_VALUE);
    }

    public AbstractHazelcastMapBackedStorageService(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, DEFAULT_PAGE_SIZE);
    }

    protected void setupSerialization() {
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
                ((AbstractSerializationService)serializationService).register(MutableStorageRecord.class, new MutableStorageRecordSerializer());
            } catch (IllegalStateException e) {
                logger.warn("Problem registering storage record serializer", e);
            }
        }
    }

    protected long getSystemExpiration(Long expiration) {
        return expiration == 0 ? 0 : expiration - System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean create(@Nonnull @NotEmpty String context, @Nonnull @NotEmpty String key, @Nonnull String value, @Nullable @Positive Long expiration) throws IOException {
        IMap backingMap = getMap(context, key);
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

    private Long doUpdate(final Long version, final String context, final String key, final String value, final Long expiration) throws IOException, VersionMismatchException {
        IMap<String, StorageRecord> backingMap = hazelcastInstance.getMap(context);
        final Lock lock = hazelcastInstance.getLock(context + ":" + key);
        lock.lock();
        try {
            MutableStorageRecord record = (MutableStorageRecord) this.doRead(context, key, null).getSecond();
            if (record == null) {
                return null;
            }

            if (version != null && version != record.getVersion()) {
                throw new VersionMismatchException();
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
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean update(@Nonnull @NotEmpty final String context, @Nonnull @NotEmpty final String key, @Nonnull @NotEmpty final String value, @Nullable @Positive final Long expiration) throws IOException {
        try {
            return doUpdate(null, context, key, value, expiration) != null;
        } catch (VersionMismatchException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Long updateWithVersion(@Positive long version, @Nonnull @NotEmpty String context, @Nonnull @NotEmpty String key, @Nonnull String value, @Nullable @Positive Long expiration) throws IOException, VersionMismatchException {
        return doUpdate(version, context, key, value, expiration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateExpiration(@Nonnull String context, @Nonnull String key, @Nullable Long expiration) throws IOException {
        try {
            return doUpdate(null, context, key, null, expiration) != null;
        } catch (VersionMismatchException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean doDelete(Long version, String context, String key) throws IOException, VersionMismatchException {
        IMap backingMap = getMap(context, key);
        Object ikey = getKey(context, key);
        if (!backingMap.containsKey(ikey)) {
            return false;
        }
        if (version != null && ((StorageRecord)backingMap.get(ikey)).getVersion() != version) {
            throw new VersionMismatchException();
        }
        backingMap.delete(ikey);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(@Nonnull String context, @Nonnull String key) throws IOException {
        try {
            return doDelete(null, context, key);
        } catch (VersionMismatchException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteWithVersion(long version, @Nonnull String context, @Nonnull String key) throws IOException, VersionMismatchException {
        return doDelete(version, context, key);
    }

    /**
     * {@inheritDoc}
     *
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
}
