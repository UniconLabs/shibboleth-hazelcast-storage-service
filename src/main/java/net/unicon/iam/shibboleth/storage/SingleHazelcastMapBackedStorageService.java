package net.unicon.iam.shibboleth.storage;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import org.opensaml.storage.StorageRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

/**
 * Implementation of {@link org.opensaml.storage.AbstractMapBackedStorageService} that uses
 * Hazelcast for storage. This implementation will use a single named IMap for storage.
 */
public class SingleHazelcastMapBackedStorageService extends AbstractHazelcastMapBackedStorageService {
    private final String mapName;

    public SingleHazelcastMapBackedStorageService(String mapName, HazelcastInstance hazelcastInstance, int pageSize) {
        super(hazelcastInstance, pageSize);
        this.mapName = mapName;
    }

    public SingleHazelcastMapBackedStorageService(String mapName, HazelcastInstance hazelcastInstance) {
        super(hazelcastInstance);
        this.mapName = mapName;
    }

    @Override
    protected IMap<Object, StorageRecord> getMap(String context, String key) {
        return this.hazelcastInstance.getMap(mapName);
    }

    @Override
    protected Object getKey(String context, String key) {
        return new CompositeKey(context, key);
    }

    private Set getContextKeySet(String context) {
        IMap backingMap = this.getMap(context, null);
        EntryObject e = new PredicateBuilder().getEntryObject();
        Predicate contextPredicate = e.key().get("context").equal(context);
        return backingMap.keySet(contextPredicate);
    }

    @Override
    public void updateContextExpiration(@Nonnull @NotEmpty String context, @Nullable Long expiration) throws IOException {
        IMap backingMap = this.getMap(context, null);
        for (Object key: this.getContextKeySet(context)) {
            this.updateExpiration(((CompositeKey)key).getContext(), ((CompositeKey)key).getKey(), expiration);
        }

    }

    @Override
    public void deleteContext(@Nonnull @NotEmpty String context) throws IOException {
        IMap backingMap = this.getMap(context, null);
        Set keySet = this.getContextKeySet(context);
        keySet.forEach(backingMap::delete);
    }

    public static class CompositeKey implements Serializable {
        private final String context;
        private final String key;

        public CompositeKey(final String context, final String key){
            this.context = context;
            this.key = key;
        }

        public String getContext() { return this.context; }
        public String getKey() { return this.key; }

        @Override
        public int hashCode() {
            return (context + ":" + key).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CompositeKey)) {
                return false;
            }
            CompositeKey that = (CompositeKey)obj;
            return this.context.equals(that.context) && this.key.equals(that.key);
        }
    }
}
