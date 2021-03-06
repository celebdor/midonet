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

package org.midonet.midolman.topology

import java.util.UUID

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import org.junit.runner.RunWith
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner

import rx.Observable

import org.midonet.cluster.data.storage._
import org.midonet.cluster.models.Topology.Route.NextHop
import org.midonet.cluster.models.Topology.{Port => TopologyPort, Route => TopologyRoute, Router => TopologyRouter}
import org.midonet.cluster.services.MidonetBackend
import org.midonet.cluster.util.UUIDUtil._
import org.midonet.midolman.layer3.{InvalidationTrie, Route}
import org.midonet.midolman.simulation.{Router => SimulationRouter}
import org.midonet.midolman.topology.TopologyTest.DeviceObserver
import org.midonet.midolman.util.MidolmanSpec
import org.midonet.odp.FlowMatch
import org.midonet.packets.{IPAddr, IPSubnet, IPv4Addr, MAC}
import org.midonet.sdn.flows.FlowTagger.{tagForDestinationIp, tagForRoute}

@RunWith(classOf[JUnitRunner])
class RouterMapperTest extends MidolmanSpec with TopologyBuilder
                               with TopologyMatchers with Eventually {

    import org.midonet.midolman.topology.TopologyBuilder._

    private var store: StorageWithOwnership = _
    private var vt: VirtualTopology = _
    private var threadId: Long = _

    private final val timeout = 5 seconds

    protected override def beforeTest(): Unit = {
        vt = injector.getInstance(classOf[VirtualTopology])
        store = injector.getInstance(classOf[MidonetBackend]).ownershipStore
        threadId = Thread.currentThread.getId
    }

    implicit def asIPSubnet(str: String): IPSubnet[_] = IPSubnet.fromString(str)
    implicit def asIPAddres(str: String): IPAddr = IPv4Addr(str)
    implicit def asMAC(str: String): MAC = MAC.fromString(str)
    implicit def asRoute(str: String): Route =
        new Route(0, 0, IPv4Addr(str).toInt, 32, null, null, 0, 0, null, null)

    def flowOf(srcAddress: String, dstAddress: String): FlowMatch = {
        new FlowMatch()
            .setNetworkSrc(IPAddr.fromString(srcAddress))
            .setNetworkDst(IPAddr.fromString(dstAddress))
    }

    private def createObserver(): DeviceObserver[SimulationRouter] = {
        Given("An observer for the router mapper")
        // It is possible to receive the initial notification on the current
        // thread, when the device was notified in the mapper's behavior subject
        // previous to the subscription.
        new DeviceObserver[SimulationRouter](vt)
    }

    private def createExteriorPort(routerId: UUID, adminStateUp: Boolean = true)
    : TopologyPort = {
        val hostId = UUID.randomUUID()
        store.create(createHost(id = hostId))
        createRouterPort(routerId = Some(routerId),
                         adminStateUp = adminStateUp,
                         hostId = Some(hostId),
                         interfaceName = Some("iface0"))
    }

    private def testRouterCreated(obs: DeviceObserver[SimulationRouter])
    : (TopologyRouter, RouterMapper) = {
        Given("A router mapper")
        val routerId = UUID.randomUUID
        val mapper = new RouterMapper(routerId, vt)

        And("A router")
        val router = createRouter(id = routerId)

        When("The router is created")
        store.create(router)

        And("The observer subscribes to an observable on the mapper")
        Observable.create(mapper).subscribe(obs)

        Then("The observer should receive the router device")
        obs.awaitOnNext(1, timeout) shouldBe true
        val device = obs.getOnNextEvents.get(0)
        device shouldBeDeviceOf router

        (router, mapper)
    }

    private def testRouterUpdated(router: TopologyRouter,
                                  obs: DeviceObserver[SimulationRouter],
                                  event: Int): SimulationRouter = {
        When("The router is updated")
        store.update(router)

        Then("The observer should receive the update")
        obs.awaitOnNext(event, timeout) shouldBe true
        val device = obs.getOnNextEvents.get(event - 1)
        device shouldBeDeviceOf router

        device
    }

    private def testRouterDeleted(routerId: UUID,
                                  obs: DeviceObserver[SimulationRouter]): Unit = {
        When("The router is deleted")
        store.delete(classOf[TopologyRouter], routerId)

        Then("The observer should receive a completed notification")
        obs.awaitCompletion(timeout)
        obs.getOnCompletedEvents should not be empty
    }

    feature("Router mapper emits notifications for router update") {
        scenario("The mapper emits error for non-existing router") {
            Given("A router identifier")
            val routerId = UUID.randomUUID

            And("A router mapper")
            val mapper = new RouterMapper(routerId, vt)

            And("An observer to the router mapper")
            val obs = createObserver()

            When("The observer subscribes to an observable on the mapper")
            Observable.create(mapper).subscribe(obs)

            Then("The observer should see a NotFoundException")
            obs.awaitCompletion(timeout)
            val e = obs.getOnErrorEvents.get(0).asInstanceOf[NotFoundException]
            e.clazz shouldBe classOf[TopologyRouter]
            e.id shouldBe routerId
        }

        scenario("The mapper emits existing router") {
            val obs = createObserver()
            testRouterCreated(obs)
        }

        scenario("The mapper emits new device on router update") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1
            val routerUpdate = createRouter(id = router.getId, adminStateUp = true)
            testRouterUpdated(routerUpdate, obs, event = 2)
        }

        scenario("The mapper completes on router delete") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1
            testRouterDeleted(router.getId, obs)
        }
    }

    feature("Test port route updates") {
        scenario("Create exterior port without route") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            And("The router should have the administrative state down")
            val device1 = obs.getOnNextEvents.get(0)
            device1.cfg.adminStateUp shouldBe false

            When("Creating an exterior port for the router")
            val port = createExteriorPort(router.getId)
            store.create(port)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true
            val device2 = obs.getOnNextEvents.get(1)
            device2 shouldBeDeviceOf router
        }

        scenario("Create exterior port with a route, port inactive") {
            val obs = createObserver()
            val router1 = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val port = createExteriorPort(router1.getId)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateOp(port), CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            And("Waiting for the first router notification")
            obs.awaitOnNext(2, timeout)

            And("Updating the router to generate another notification")
            val router2 = router1.addPortId(port.getId).setAdminStateUp(true)
            store.update(router2)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router2
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }

        scenario("Create exterior port with a route, port active up") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val port = createExteriorPort(router.getId)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, UUID.randomUUID.toString),
                            CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(1)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.setNextHopPortId(port.getId).asJava
        }

        scenario("Create exterior port with a route, port active down") {
            val obs = createObserver()
            val router1 = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val port = createExteriorPort(router1.getId, adminStateUp = false)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, UUID.randomUUID.toString),
                            CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            And("Waiting for the first router notification")
            obs.awaitOnNext(2, timeout) shouldBe true

            And("Updating the router to generate another notification")
            val router2 = router1.addPortId(port.getId).setAdminStateUp(true)
            store.update(router2)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router2
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }

        scenario("Create interior port with a route, admin state up") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an interior port with a route")
            val portId = UUID.randomUUID
            val routeId = UUID.randomUUID
            val peerPortId = UUID.randomUUID
            val port = createRouterPort(id = portId,
                                        routerId = Some(router.getId),
                                        adminStateUp = true)
            val peerPort = createBridgePort(id = peerPortId)
            val route = createRoute(id = routeId,
                                  srcNetwork = "1.0.0.0/24",
                                  dstNetwork = "2.0.0.0/24",
                                  nextHop = NextHop.PORT)
            store.multi(Seq(CreateOp(port), CreateOp(peerPort), CreateOp(route),
                            UpdateOp(port.setPeerId(peerPortId)),
                            UpdateOp(route.setNextHopPortId(portId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(1)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.setNextHopPortId(portId).asJava
        }

        scenario("Create interior port with a route, admin state down") {
            val obs = createObserver()
            val router1 = testRouterCreated(obs)._1

            When("Creating an interior port with a route")
            val portId = UUID.randomUUID
            val routeId = UUID.randomUUID
            val peerPortId = UUID.randomUUID
            val port = createRouterPort(id = portId,
                                        routerId = Some(router1.getId),
                                        adminStateUp = false)
            val peerPort = createBridgePort(id = peerPortId)
            val route = createRoute(id = routeId,
                                  srcNetwork = "1.0.0.0/24",
                                  dstNetwork = "2.0.0.0/24",
                                  nextHop = NextHop.PORT)
            store.multi(Seq(CreateOp(port), CreateOp(peerPort), CreateOp(route),
                            UpdateOp(port.setPeerId(peerPortId)),
                            UpdateOp(route.setNextHopPortId(portId))))

            And("Waiting for the first router notification")
            obs.awaitOnNext(2, timeout)

            And("Updating the router to generate another notification")
            val router2 = router1.addPortId(portId).setAdminStateUp(true)
            store.update(router2)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router2
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }

        scenario("Route removed when exterior port becomes inactive") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val ownerId = UUID.randomUUID.toString
            val port = createExteriorPort(router.getId)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, ownerId),
                            CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The port becomes inactive")
            store.deleteOwner(classOf[TopologyPort], port.getId, ownerId)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }

        scenario("Route removed when exterior port becomes down") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val ownerId = UUID.randomUUID.toString
            val port = createExteriorPort(router.getId)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, ownerId),
                            CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The port becomes administratively down")
            store.update(port.addRouteId(route.getId).setAdminStateUp(false))

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }

        scenario("Route removed when interior port becomes down") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an interior port with a route")
            val portId = UUID.randomUUID
            val routeId = UUID.randomUUID
            val peerPortId = UUID.randomUUID
            val port = createRouterPort(id = portId,
                                        routerId = Some(router.getId),
                                        adminStateUp = true)
            val peerPort = createBridgePort(id = peerPortId)
            val route = createRoute(id = routeId,
                                    srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateOp(port), CreateOp(peerPort), CreateOp(route),
                            UpdateOp(port.setPeerId(peerPortId)),
                            UpdateOp(route.setNextHopPortId(portId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The port becomes inactive")
            store.update(port.addRouteId(route.getId).setPeerId(peerPortId)
                             .setAdminStateUp(false))

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }

        scenario("Route added when active exterior port becomes up") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an inactive exterior port with a route")
            val ownerId = UUID.randomUUID.toString
            val port = createExteriorPort(router.getId, adminStateUp = false)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, ownerId), CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The port becomes up")
            store.update(port.addRouteId(route.getId).setAdminStateUp(true))

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.setNextHopPortId(port.getId).asJava
        }

        scenario("Route added when active exterior port becomes active") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an inactive exterior port with a route")
            val ownerId = UUID.randomUUID.toString
            val port = createExteriorPort(router.getId)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateOp(port), CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The port becomes active")
            store.updateOwner(classOf[TopologyPort], port.getId, ownerId,
                              throwIfExists = true)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.setNextHopPortId(port.getId).asJava
        }

        scenario("Route added when interior port becomes up") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an interior port with a route")
            val portId = UUID.randomUUID
            val routeId = UUID.randomUUID
            val peerPortId = UUID.randomUUID
            val port = createRouterPort(id = portId,
                                        routerId = Some(router.getId),
                                        adminStateUp = false)
            val peerPort = createBridgePort(id = peerPortId)
            val route = createRoute(id = routeId,
                                    srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateOp(port), CreateOp(peerPort), CreateOp(route),
                            UpdateOp(port.setPeerId(peerPortId)),
                            UpdateOp(route.setNextHopPortId(portId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The port becomes up")
            store.update(port.addRouteId(route.getId).setPeerId(peerPortId)
                             .setAdminStateUp(true))

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.setNextHopPortId(port.getId).asJava
        }

        scenario("Port route added") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an exterior port")
            val port = createExteriorPort(router.getId)
            store.multi(Seq(CreateWithOwnerOp(port, UUID.randomUUID.toString)))

            Then("The observer should receive a router update and no routes")
            obs.awaitOnNext(2, timeout) shouldBe true
            val device1 = obs.getOnNextEvents.get(1)
            device1 shouldBeDeviceOf router
            device1.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty

            When("Adding a route to the port")
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT,
                                    nextHopPortId = Some(port.getId))
            store.create(route)

            Then("The observer should receive a router update with the route")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device2 = obs.getOnNextEvents.get(2)
            device2 shouldBeDeviceOf router
            device2.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.setNextHopPortId(port.getId).asJava
        }

        scenario("Port route updated") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val port = createExteriorPort(router.getId)
            val route1 = createRoute(srcNetwork = "1.0.0.0/24",
                                     dstNetwork = "2.0.0.0/24",
                                     nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, UUID.randomUUID.toString),
                            CreateOp(route1),
                            UpdateOp(route1.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The route is updated")
            val route2 = route1.setDstNetwork("3.0.0.0/24")
                               .setNextHopPortId(port.getId)
            store.update(route2)

            Then("The observer should receive a router update with new route")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
            device.rTable.lookup(flowOf("1.0.0.0", "3.0.0.0")) should contain only
                route2.asJava
        }

        scenario("Port route removed") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Creating an exterior port with a route")
            val port = createExteriorPort(router.getId)
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    nextHop = NextHop.PORT)
            store.multi(Seq(CreateWithOwnerOp(port, UUID.randomUUID.toString),
                            CreateOp(route),
                            UpdateOp(route.setNextHopPortId(port.getId))))

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The route is deleted")
            store.delete(classOf[TopologyRoute], route.getId)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }
    }

    feature("Test router route updates") {
        scenario("Router route added") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Adding a route to the router")
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    routerId = Some(router.getId))
            store.create(route)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(1)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) should contain only
                route.asJava
        }

        scenario("Router route updated") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Adding a route to the router")
            val route1 = createRoute(srcNetwork = "1.0.0.0/24",
                                     dstNetwork = "2.0.0.0/24",
                                     routerId = Some(router.getId))
            store.create(route1)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The route is updated")
            val route2 = route1.setDstNetwork("3.0.0.0/24")
            store.update(route2)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
            device.rTable.lookup(flowOf("1.0.0.0", "3.0.0.0")) should contain only
                route2.asJava
        }

        scenario("Router route deleted") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Adding a route to the router")
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    routerId = Some(router.getId))
            store.create(route)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The route is deleted")
            store.delete(classOf[TopologyRoute], route.getId)

            Then("The observer should receive a router update")
            obs.awaitOnNext(3, timeout) shouldBe true
            val device = obs.getOnNextEvents.get(2)
            device shouldBeDeviceOf router
            device.rTable.lookup(flowOf("1.0.0.0", "2.0.0.0")) shouldBe empty
        }
    }

    feature("Test router ARP table") {
        scenario("The router mapper creates a unique ARP cache") {
            val obs = createObserver()
            val router1 = testRouterCreated(obs)._1
            val arpCache = obs.getOnNextEvents.get(0).arpCache

            When("The router is updated")
            val router2 = router1.setAdminStateUp(!router1.getAdminStateUp)
            store.update(router2)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            And("The ARP table should be the same instance")
            (obs.getOnNextEvents.get(1).arpCache eq arpCache) shouldBe true
        }
    }

    feature("Test router tag manager") {
        scenario("The router mapper updates the flow count map") {
            val obs = createObserver()
            val mapper = testRouterCreated(obs)._2
            val tagManager = obs.getOnNextEvents.get(0).routerMgrTagger

            When("Adding a destination IP")
            tagManager.addTag("10.0.0.1")

            Then("The tag to flow count should contain the IP")
            eventually {
                mapper.tagToFlowCount should contain (IPv4Addr("10.0.0.1") -> 1)
            }

            When("Adding the IP a second time")
            tagManager.addTag("10.0.0.1")

            Then("The tag to flow count should increment the count")
            eventually {
                mapper.tagToFlowCount should contain (IPv4Addr("10.0.0.1") -> 2)
            }

            When("Removing the IP")
            val callback = tagManager.getFlowRemovalCallback("10.0.0.1")
            callback.call()

            Then("The tag to flow count should decrement the count")
            eventually {
                mapper.tagToFlowCount should contain (IPv4Addr("10.0.0.1") -> 1)
            }

            When("Removing the IP a second time")
            callback.call()

            Then("The tag to flow count should remove the IP")
            eventually {
                mapper.tagToFlowCount should not contain key(IPv4Addr("10.0.0.1"))
            }
        }

        scenario("The router mapper updates the tag trie") {
            val obs = createObserver()
            val mapper = testRouterCreated(obs)._2
            val tagManager = obs.getOnNextEvents.get(0).routerMgrTagger

            When("Adding a destination IP")
            tagManager.addTag("10.0.0.1")

            Then("The invalidation trie should contain the IP")
            eventually {
                val subTree = mapper.dstIpTagTrie.projectRouteAndGetSubTree("10.0.0.1")
                val ips = InvalidationTrie.getAllDescendantsIpDestination(subTree)
                ips.asScala should contain only IPv4Addr("10.0.0.1")
            }

            When("Removing the destination IP")
            tagManager.getFlowRemovalCallback("10.0.0.1").call()

            Then("The invalidation trie should not contain the IP")
            eventually {
                val subTree = mapper.dstIpTagTrie.projectRouteAndGetSubTree("10.0.0.1")
                val ips = InvalidationTrie.getAllDescendantsIpDestination(subTree)
                ips.asScala shouldBe empty
            }
        }
    }

    feature("Test flow invalidation") {
        scenario("For added route corresponding to destination IP") {
            val obs = createObserver()
            val mapper = testRouterCreated(obs)._2
            val tagManager = obs.getOnNextEvents.get(0).routerMgrTagger

            When("Adding a destination IP")
            tagManager.addTag("2.0.0.1")

            When("Adding a route to the router")
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    routerId = Some(mapper.id))
            store.create(route)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            And("The flow controller should receive the IP invalidation")
            flowInvalidator should invalidate (tagForDestinationIp(mapper.id, "2.0.0.1"))
        }

        scenario("For removed route") {
            val obs = createObserver()
            val router = testRouterCreated(obs)._1

            When("Adding a route to the router")
            val route = createRoute(srcNetwork = "1.0.0.0/24",
                                    dstNetwork = "2.0.0.0/24",
                                    routerId = Some(router.getId))
            store.create(route)

            Then("The observer should receive a router update")
            obs.awaitOnNext(2, timeout) shouldBe true

            When("The route is deleted")
            store.delete(classOf[TopologyRoute], route.getId)

            And("Waiting for the router updates")
            obs.awaitOnNext(3, timeout) shouldBe true

            Then("The flow controller should receive a route invalidation")
            flowInvalidator should invalidate (tagForRoute(route.asJava))
        }
    }
}
