package net.unicon.iam.shibboleth.storage.hazelcast;

import org.opensaml.storage.MutableStorageRecord;

/**
 * {@link MutableStorageRecord} implementation that allows the setting of the version. Used for the Hazelcast
 * serialization engine.
 */
public class VersionMutableStorageRecord extends MutableStorageRecord {
    public VersionMutableStorageRecord(String value, Long expiration, Long version) {
        super(value, expiration);
        super.setVersion(version);
    }
}