/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.cluster.state

import java.util.UUID

import javax.annotation.Nonnull

import com.google.inject.Inject
import com.google.inject.name.Named

import org.apache.zookeeper.CreateMode.EPHEMERAL

import rx.Observable
import rx.subjects.PublishSubject

import org.midonet.cluster.data.storage.{NotFoundException, OwnershipConflictException}
import org.midonet.cluster.models.Topology.Port
import org.midonet.cluster.services.MidonetBackend
import org.midonet.cluster.storage.MidonetBackendConfig
import org.midonet.cluster.{ClusterRouterManager, DataClient}
import org.midonet.midolman.layer3.Route
import org.midonet.midolman.logging.MidolmanLogging
import org.midonet.midolman.serialization.{SerializationException, Serializer}
import org.midonet.midolman.simulation.Bridge.UntaggedVlanId
import org.midonet.midolman.state.zkManagers.{PortZkManager, RouterZkManager}
import org.midonet.midolman.state.{PortConfig, PortDirectory, StateAccessException, _}
import org.midonet.util.eventloop.Reactor
import org.midonet.util.functors._

/**
 * An implementation of the [[StateStorage]] trait using the legacy ZooKeeper
 * managers as backend.
 */
class ZookeeperStateStorage @Inject() (backendCfg: MidonetBackendConfig,
                                       backend: MidonetBackend,
                                       dataClient: DataClient,
                                       @Named("directoryReactor") reactor: Reactor,
                                       connectionWatcher: ZkConnectionAwareWatcher,
                                       // Legacy
                                       serializer: Serializer,
                                       zkManager: ZkManager,
                                       portZkManager: PortZkManager,
                                       routerZkManager: RouterZkManager,
                                       routerManager: ClusterRouterManager,
                                       pathBuilder: PathBuilder)
        extends StateStorage with MidolmanLogging {

    /**
     * An implementation of a route replicated set.
     */
    @throws[StateAccessException]
    private class ReplicatedRouteTable(routerId: UUID)
        extends ReplicatedSet[Route](
            routerZkManager.getRoutingTableDirectory(routerId), EPHEMERAL) {

        protected override def encode(route: Route): String = {
            try {
                new String(serializer.serialize(route))
            } catch {
                case e: Throwable =>
                    log.error("Could not serialize route {}", route, e)
                    null
            }
        }

        protected override def decode(str: String): Route = {
            try {
                serializer.deserialize(str.getBytes, classOf[Route])
            } catch {
                case e: Throwable =>
                    log.error("Could not deserialize route {}", str, e)
                    null
            }
        }
    }

    private val subjectLocalPortActive = PublishSubject.create[LocalPortActive]

    override def logSource = "org.midonet.cluster.state"

    @throws[StateAccessException]
    override def bridgeMacTable(@Nonnull bridgeId: UUID, vlanId: Short,
                                ephemeral: Boolean): MacPortMap = {
        ensureBridgePaths(bridgeId)
        ensureBridgeVlanPaths(bridgeId, vlanId)
        val map = dataClient.bridgeGetMacTable(bridgeId, vlanId, ephemeral)
        map.setConnectionWatcher(connectionWatcher)
        map
    }

    @throws[StateAccessException]
    override def bridgeIp4MacMap(@Nonnull bridgeId: UUID): Ip4ToMacReplicatedMap = {
        ensureBridgePaths(bridgeId)
        val map = dataClient.getIp4MacMap(bridgeId)
        map.setConnectionWatcher(connectionWatcher)
        map
    }

    @throws[StateAccessException]
    override def routerRoutingTable(@Nonnull routerId: UUID)
    : ReplicatedSet[Route] = {
        ensureRouterPaths(routerId)
        val routingTable = new ReplicatedRouteTable(routerId)
        routingTable.setConnectionWatcher(connectionWatcher)
        routingTable
    }

    @throws[StateAccessException]
    override def routerArpTable(@Nonnull routerId: UUID): ArpTable = {
        ensureRouterPaths(routerId)
        val arpTable = new ArpTable(routerZkManager
                                        .getArpTableDirectory(routerId))
        arpTable.setConnectionWatcher(connectionWatcher)
        arpTable
    }

    override def setPortLocalAndActive(portId: UUID, hostId: UUID,
                                       active: Boolean): Unit = runOnReactor {
        // Activate the port for legacy ZK storage.
        if (!backendCfg.useNewStack) {
            portZkManager.setActivePort(portId, hostId, active)
                .observeOn(reactor.rxScheduler)
                .flatMap(makeFunc1[Void,Observable[PortConfig]](
                             (x: Void) => { portZkManager.getWithObservable(portId) }))
                .doOnNext(makeAction1[PortConfig](portConfig => {
                             if (portConfig.isInstanceOf[PortDirectory.RouterPortConfig]) {
                                 val deviceId: UUID = portConfig.device_id
                                 routerManager.updateRoutesBecauseLocalPortChangedStatus(
                                     deviceId, portId, active)
                             }
                         }))
                .subscribe(makeAction1[PortConfig](
                               (p) => { subjectLocalPortActive.onNext(
                                           LocalPortActive(portId, active)) }),
                           makeAction1[Throwable](
                               (t: Throwable) => {
                                   t match {
                                       case e: StateAccessException =>
                                           log.error("Error retrieving the configuration for port {}",
                                                     portId, e)
                                       case e: SerializationException =>
                                           log.error("Error serializing the configuration for port {}",
                                                     portId, e)
                                   }
                               }))
        }

        // Activate the port for cluster storage.
        if (backend.isEnabled) {
            try {
                val storage = backend.ownershipStore
                if (active) {
                    storage.updateOwner(classOf[Port], portId, hostId,
                                        throwIfExists = true)
                } else {
                    storage.deleteOwner(classOf[Port], portId, hostId)
                }
            } catch {
                case e: NotFoundException =>
                    log.error("Port {} does not exist", portId)
                case e: OwnershipConflictException =>
                    log.error("Host {} does not have permission to activate " +
                              "or deactivate port {}", hostId, portId)
            }
            subjectLocalPortActive.onNext(
                                LocalPortActive(portId, active))
        }
    }

    override def localPortActiveObservable: Observable[LocalPortActive] =
        subjectLocalPortActive.asObservable

    private def runOnReactor(fn: => Unit) = reactor.submit(makeRunnable(fn))

    /** Ensures that the path for the specified bridge is created in the
      * legacy storage. */
    @throws[StateAccessException]
    private def ensureBridgePaths(bridgeId: UUID) = {
        // Create path.
        val bridgePath = pathBuilder.getBridgePath(bridgeId)
        val bridgeMacPortsPath =
            pathBuilder.getBridgeMacPortsPath(bridgeId, UntaggedVlanId)
        val bridgeVlansPath = pathBuilder.getBridgeVlansPath(bridgeId)

        // Create the bridge path if it does not exist.
        log.info("Creating bridge {} path in state storage.", bridgeId)
        createPath(bridgePath)
        createPath(bridgeMacPortsPath)
        createPath(bridgeVlansPath)
    }

    /** Ensures that the path for the specified bridge and VLAN is created in
      * the legacy storage. */
    @throws[StateAccessException]
    private def ensureBridgeVlanPaths(bridgeId: UUID, vlanId: Short): Unit = {
        // Create the VLAN if different from the default VLAN.
        if (vlanId != UntaggedVlanId) {

            log.info("Creating bridge {} VLAN {} path in state storage.",
                     bridgeId, Short.box(vlanId))

            val bridgeVlanPath = pathBuilder.getBridgeVlanPath(bridgeId, vlanId)
            val bridgeVlanMacPortsPath =
                pathBuilder.getBridgeMacPortsPath(bridgeId, vlanId)
            createPath(bridgeVlanPath)
            createPath(bridgeVlanMacPortsPath)
        }
    }

    /** Ensures that the path for the specified router is created in the legacy
      * storage. */
    @throws[StateAccessException]
    private def ensureRouterPaths(routerId: UUID): Unit = {
        // Create path.
        val routerPath = pathBuilder.getRouterPath(routerId)
        val routerArpTablePath = pathBuilder.getRouterArpTablePath(routerId)
        val routerRoutingTablePath = pathBuilder.getRouterRoutingTablePath(routerId)

        // Create the router path if it does not exist.
        log.info("Creating router {} path in state storage.", routerId)
        createPath(routerPath)
        createPath(routerArpTablePath)
        createPath(routerRoutingTablePath)
    }

    /** Creates a path with no data in the legacy storage */
    private def createPath(path: String): Unit = {
        try {
            log.debug("State storage create path {}", path)
            zkManager.addPersistent(path, new Array[Byte](0))
        } catch {
            case e: StatePathExistsException =>
                log.debug("Path {} already exists in legacy storage", path)
            case e: StateAccessException =>
                log.error("Failed to create path {} in legacy storage", path, e)
        }
    }
}
