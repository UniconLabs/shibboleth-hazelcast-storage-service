# Shibboleth Hazelcast storage

**Note: this is for Shibboleth IdP v3**

## Acquire Distribution

There are two options for getting the distribution: downloading a prebuilt archive or building from source.

### Prebuilt Archive

A distribution may be downloaded from the project's Bintray page: [https://bintray.com/uniconiam/generic/shibboleth-hazelcast-storage-service](https://bintray.com/uniconiam/generic/shibboleth-hazelcast-storage-service)

### Building from Source

1. `git clone https://github.com/UniconLabs/shibboleth-hazelcast-storage-service.git`
1. `cd shibboleth-hazelcast-storage-service`
1. `./gradlew clean build`

This will create 2 files in `build/distributions`: a zip file and a tar file.

## Installation

Once a distribution is acquired, unpackage the archive and you should find an `edit-webapp` directory. Place the files
in this new directory in the appropriate `edit-webapp` for your IdP. Rebuild and redeploy the `idp.war` file.

## Configuration

In `global.xml`:

```
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

<bean id="my.HazelcastStorageService"
      class="net.unicon.iam.shibboleth.storage.HazelcastMapBackedStorageService">
    <constructor-arg name="hazelcastInstance" ref="hazelcast" />
</bean>

<bean id="my.StorageService.cas"
        class="net.unicon.iam.shibboleth.storage.SingleHazelcastMapBackedStorageService">
    <constructor-arg value="cas" />
    <constructor-arg ref="hazelcast" />
</bean>

<bean id="my.StorageService.idpSession"
      class="net.unicon.iam.shibboleth.storage.SingleHazelcastMapBackedStorageService">
    <constructor-arg value="session" />
    <constructor-arg ref="hazelcast" />
</bean>
```

Note that you can configure the `HazelcastInstance` in the Spring configuration file or use one of the other configuration
methods.

The above configuration shows two ways of using Hazelcast:

1. `my.HazelcastStorageService` will dynamically create maps based upon the name of the context.
1. `my.StorageService.cas` and `my.StorageService.idpSession` creates the maps named in the first `constructor-arg`. This
allows for finer, explicit control of the Hazelcast maps.

For more information about configuring Hazelcast, see [http://hazelcast.org/documentation/](http://hazelcast.org/documentation/).

In `idp.properties`, set each of the storage services you want to use Hazelcast to one of the configured Hazelcast stores:

* idp.session.StorageService
* idp.cas.StorageService
* idp.consent.StorageService
* idp.replayCache.StorageService
* idp.artifact.StorageService

## Licensing

Licensed under the terms of the Apache License, v2. Please see [LICENSE](LICENSE) or [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0) for more information.

## Acknowledgements

This library was developed in cooperation with:

* Portland State University
* Unicon Open Source Support Subscribers
