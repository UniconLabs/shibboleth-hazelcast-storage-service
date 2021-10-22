# Shibboleth Identity Provider Hazelcast Storage Service

This package provides a storage service implementation for the Shibboleth IdP (v4.1 or later) that is based on Hazelcast v4 [ http://hazelcast.org ].
The service is deployed as a Shibboleth Plugin (see [ https://shibboleth.atlassian.net/wiki/spaces/IDP4/pages/1294074003/PluginInstallation ])

## System Requirements

- Shibboleth IdP v4.1

## Getting started

1. Download the distribution from [ TBD ]. Download either the `.tar.gz` or `.zip` file **and** the associated GPG signature file (the `.asc` file).
2. Install the plugin following instructions at - [ https://shibboleth.atlassian.net/wiki/spaces/IDP4/pages/1294074003/PluginInstallation ]
3. Add storage service bean to `global.xml`. For example:

```xml
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
      class="HazelcastMapBackedStorageService">
    <constructor-arg name="hazelcastInstance" ref="hazelcast" />
</bean>

<bean id="my.StorageService.cas"
        class="SingleHazelcastMapBackedStorageService">
    <constructor-arg value="cas" />
    <constructor-arg ref="hazelcast" />
</bean>

<bean id="my.StorageService.idpSession"
      class="SingleHazelcastMapBackedStorageService">
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

## Included libraries and dependency tree

        \--- com.hazelcast:hazelcast-all:4.2.2


## Acknowledgements

This library was developed in cooperation with:

* Portland State University
* Unicon Open Source Support Subscribers