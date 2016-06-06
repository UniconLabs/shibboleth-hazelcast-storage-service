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

        if (object.getExpiration() != null) {
            out.writeLong(object.getExpiration());
        } else {
            out.writeLong(-1);
        }
    }

    @Override
    public MutableStorageRecord read(ObjectDataInput in) throws IOException {
        long version = in.readLong();
        String value = in.readUTF();
        Long expiration = in.readLong();
        if (expiration == -1) {
            expiration = null;
        }
        return new VersionMutableStorageRecord(value, expiration, version);
    }

    @Override
    public int getTypeId() {
        return 12345;
    }

    @Override
    public void destroy() {

    }
}
