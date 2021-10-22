package net.unicon.iam.shibboleth.storage.hazelcast.plugin;

import net.shibboleth.idp.plugin.PluginException;
import net.shibboleth.idp.plugin.PropertyDrivenIdPPlugin;

import java.io.IOException;

public class HazelcastStorageServicePlugin extends PropertyDrivenIdPPlugin {

    public HazelcastStorageServicePlugin() throws PluginException, IOException {
        super(HazelcastStorageServicePlugin.class);
    }
}