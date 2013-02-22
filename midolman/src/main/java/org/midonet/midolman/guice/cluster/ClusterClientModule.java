/*
* Copyright 2012 Midokura Europe SARL
*/
package org.midonet.midolman.guice.cluster;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import org.midonet.midolman.config.MidolmanConfig;
import org.midonet.midolman.config.ZookeeperConfig;
import org.midonet.midolman.guice.zookeeper.ZKConnectionProvider;
import org.midonet.midolman.state.Directory;
import org.midonet.midolman.state.PortConfigCache;
import org.midonet.midolman.state.zkManagers.PortGroupZkManager;
import org.midonet.midolman.state.zkManagers.PortZkManager;
import org.midonet.cluster.*;
import org.midonet.util.eventloop.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines dependency bindings for DataClient and Client
 * interfaces.  It extends DataClusterClientModule that defines bindings
 * for DataClient, and it defines the bindings specific to Client.
 */
public class ClusterClientModule extends DataClusterClientModule {

    private static final Logger log = LoggerFactory
            .getLogger(ClusterClientModule.class);

    @Override
    protected void configure() {
        super.configure();

        requireBinding(Directory.class);

        bind(Client.class)
                .to(LocalClientImpl.class)
                .asEagerSingleton();
        expose(Client.class);
    }
}