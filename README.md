# Shibboleth Hazelcast storage

**Note: this is for Shibboleth IdP v3**

**Note: this is a currently developing project. Feel free to check open tickets and create new tickets for any problems you find**

## Building

Currently, there are no binary distributions available and one must build from source.

1. `git clone https://github.com/UniconLabs/shibboleth-hazelcast-storage-service.git`
1. `cd shibboleth-hazelcast-storage-service`
1. `git clean build`

This will create 2 files in `build/distributions`: a zip file and a tar file. Unpackage one appropriate for your platform
and you should find an `edit-webapp` directory. Place the files in this new directory in the appropriate `edit-webapp`
for your IdP. Rebuild and redeploy the `idp.war` file.

## Configuration

In `global.xml`:

```
<!--
<bean id="hazelcast" class="com.hazelcast.core.Hazelcast" factory-method="newHazelcastInstance">
    <constructor-arg name="config">
        <bean class="com.hazelcast.config.Config">
            <property name="properties">
                <util:properties>
                    <prop key="hazelcast.logging.type">slf4j</prop>
                    <prop key="hazelcast.max.no.heartbeat.seconds">5</prop>
                </util:properties>
            </property>
            <property name="networkConfig">
                <bean class="com.hazelcast.config.NetworkConfig">
                    <property name="port" value="5701"/>
                    <property name="portAutoIncrement" value="true"/>
                    <property name="join" ref="tcpIpHazelcastJoinConfig"/>
                </bean>
            </property>
        </bean>
    </constructor-arg>
</bean>

<bean id="tcpIpHazelcastJoinConfig" class="com.hazelcast.config.JoinConfig">
    <property name="multicastConfig">
        <bean class="com.hazelcast.config.MulticastConfig">
            <property name="enabled" value="false"/>
        </bean>
    </property>
    <property name="tcpIpConfig">
        <bean class="com.hazelcast.config.TcpIpConfig">
            <property name="enabled" value="true"/>
            <property name="members" value="%{hz.cluster.members:localhost}"/>
        </bean>
    </property>
</bean>
-->

<bean id="shibboleth.HazelcastStorageService"
      class="net.unicon.iam.shibboleth.storage.HazelcastMapBackedStorageService">
    <!--
    <constructor-arg name="hazelcastInstance" ref="hazelcast" />
    -->
</bean>
```

Note that you can configure the `HazelcastInstance` in the Spring configuration file or use one of the other configuration
methods.

For more information about configuring Hazelcast, see [http://hazelcast.org/documentation/](http://hazelcast.org/documentation/).

In `idp.properties`, set each of the storage services you want to use Hazelcast to `shibboleth.HazelcastStorageService:

* idp.session.StorageService
* idp.cas.StorageService
* idp.consent.StorageService
* idp.replayCache.StorageService
* idp.artifact.StorageService