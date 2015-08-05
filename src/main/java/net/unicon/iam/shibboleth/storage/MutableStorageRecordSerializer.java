package net.unicon.iam.shibboleth.storage;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.opensaml.storage.MutableStorageRecord;

import java.io.IOException;

public class MutableStorageRecordSerializer implements StreamSerializer<MutableStorageRecord> {

    @Override
    public void write(ObjectDataOutput out, MutableStorageRecord object) throws IOException {
        out.writeLong(object.getVersion());
        out.writeUTF(object.getValue());
        out.writeLong(object.getExpiration());
    }

    @Override
    public MutableStorageRecord read(ObjectDataInput in) throws IOException {
        long version = in.readLong();
        String value  = in.readUTF();
        long expiration = in.readLong();
        MutableStorageRecord mutableStorageRecord = new HazelcastMapBackedStorageService.VersionMutableStorageRecord(value, expiration, version);
        return mutableStorageRecord;
    }

    @Override
    public int getTypeId() {
        return 1234;
    }

    @Override
    public void destroy() {

    }
}
