// Copyright 2012 Midokura Inc.

package com.midokura.midolman

import akka.actor.{ActorRef, Actor}
import collection.JavaConversions._
import collection.mutable.{HashMap, MultiMap, Set}
import config.MidolmanConfig
import datapath.ErrorHandlingCallback
import java.util.UUID

import com.midokura.sdn.dp.{FlowMatch, Flow, Datapath, Packet}

import com.midokura.sdn.flows.{FlowManager, WildcardFlow}
import com.midokura.midolman.openflow.MidoMatch
import com.midokura.sdn.dp.flows.FlowAction
import javax.inject.Inject
import com.midokura.netlink.protos.OvsDatapathConnection
import com.midokura.netlink.Callback
import com.midokura.netlink.exceptions.NetlinkException
import akka.event.Logging

object FlowController {
    val Name = "FlowController"

    case class AddWildcardFlow(wFlow: WildcardFlow, outPorts: Set[UUID],
                               packet: Option[Packet])

    case class RemoveWildcardFlow(fmatch: MidoMatch)

    case class SendPacket(data: Array[Byte], actions: List[FlowAction[_]],
                          outPorts: Set[UUID])

    case class Consume(packet: Packet)
}

class FlowController extends Actor {

    import FlowController._
    import context._

    val log = Logging(context.system, this)
    var datapath: Datapath = null
    var maxDpFlows = 0
    var dpFlowRemoveBatchSize = 0

    @Inject
    var midolmanConfig:MidolmanConfig = null

    private val dpMatchToPendedPackets: MultiMap[FlowMatch, Packet] =
        new HashMap[FlowMatch, Set[Packet]] with MultiMap[FlowMatch, Packet]

    @Inject
    var datapathConnection: OvsDatapathConnection = null

    @Inject
    var flowManager: FlowManager = null

    def datapathController(): ActorRef = {
        actorFor("/user/%s" format DatapathController.Name)
    }


    override def preStart() {
        super.preStart()

        maxDpFlows = midolmanConfig.getDatapathMaxFlowCount

    }

    def receive = {
        case DatapathController.DatapathReady(dp) =>
            if (null == datapath) {
                datapath = dp
                installPacketInHook()
            }

        case packetIn(packet) =>
            handlePacketIn(packet)

        case AddWildcardFlow(wildcardFlow, outPorts, packetOption) =>
            if (!flowManager.add(wildcardFlow))
                log.error("FlowManager failed to install wildcard flow {}",
                          wildcardFlow)

            if (packetOption != None) {
                val packet = packetOption.get
                val pendedPackets =
                    dpMatchToPendedPackets.remove(packet.getMatch)
                val dpFlow = new Flow().
                    setMatch(packet.getMatch).
                    setActions(wildcardFlow.getActions)

                datapathConnection.flowsCreate(datapath, dpFlow,
                    new ErrorHandlingCallback[Flow] {
                        def onSuccess(data: Flow) {}

                        def handleError(ex: NetlinkException, timeout: Boolean) {
                            log.error(ex,
                                "Failed to install a flow {} due to {}", dpFlow,
                                if (timeout) "timeout" else "error")
                        }
                    })

                // Check whether the datapath's flow table is reaching the limit
                manageDPFlowTableSpace()

                // Send all pended packets with the same action list (unless
                // the action list is empty, which is equivalent to dropping)
                if (pendedPackets != None
                    && wildcardFlow.getActions.size() > 0) {
                    for (unpendedPacket <- pendedPackets.get) {
                        unpendedPacket.setActions(wildcardFlow.getActions)
                        datapathConnection.packetsExecute(
                            datapath, unpendedPacket)
                    }
                }
            }
            /*val evictedKernelFlows = (
                (Set[Flow]() /: evictedWcFlows)
                    (_ ++ exactFlowManager.removeByWildcard(_)))
            for (kernelFlow <- evictedKernelFlows) {
                // XXX
                // TODO: removeFlow(kernelFlow.getMatch)
            }*/

        case Consume(packet) =>
            val kernelMatch = packet.getMatch
            dpMatchToPendedPackets.remove(kernelMatch)

        case RemoveWildcardFlow(fmatch) => //XXX

        case SendPacket(data, actions, outPorts) => //XXX
    }

    /**
     * Internal message posted by the netlink callback hook when a new packet not
     * matching any flows appears on one of the datapath ports.
     *
     * @param packet the packet data
     */
    case class packetIn(packet: Packet)

    private def manageDPFlowTableSpace() {
        if (flowManager.getNumDpFlows > maxDpFlows - 5) {
            // TODO(pino): FlowManager should not remove the candidates until
            // TODO:       they're removed from the Datapath.
            for (flowMatch <-
                 flowManager.removeOldestDpFlows(dpFlowRemoveBatchSize)) {
                val flow = new Flow().setMatch(flowMatch)
                datapathConnection.flowsDelete(datapath, flow,
                    new ErrorHandlingCallback[Flow] {
                        def onSuccess(data: Flow) {}

                        def handleError(ex: NetlinkException, timeout: Boolean) {
                            log.error(ex,
                                "Failed to remove a flow {} due to {}", flow,
                                if (timeout) "timeout" else "error")
                        }
                    })
            }
        }
    }

    private def handlePacketIn(packet: Packet) {
        // In case the PacketIn notify raced a flow rule installation, see if
        // the flowManager already has a match.
        val actions = flowManager.getActionsForDpFlow(packet.getMatch)
        if (actions != null) {
            // XXX TODO: packetOut(packet, exactFlow)
            return
        }
        // Otherwise, try to create a datapath flow based on an existing
        // wildcard flow.
        val dpFlow = flowManager.createDpFlow(packet.getMatch)
        if (dpFlow != null) {
            // Check whether some existing datapath flows will need to be
            // evicted to make space for the new one.
            if (flowManager.getNumDpFlows > maxDpFlows) {
                // Evict 1000 datapath flows.
                for (dpFlow <- flowManager.removeOldestDpFlows(1000)) {
                    // XXX TODO: remove each flow via the Netlink API
                }
            }
            // XXX TODO: installFlow(kFlow, packet)
            return
        }
        else {
            // Otherwise, pass the packetIn up to the next layer for handling.
            // Keep track of these packets so that for every FlowMatch, only
            // one such call goes to the next layer.
            if (dpMatchToPendedPackets.get(packet.getMatch) == None) {
                datapathController() ! DatapathController.PacketIn(packet)
            }
            dpMatchToPendedPackets.addBinding(packet.getMatch, packet)
        }
    }

    private def installPacketInHook(): Unit = {
        log.info("Installing packet in handler")
        // TODO: try to make this cleaner (right now we are just waiting for
        // the install future thus blocking the current thread).
        datapathConnection.datapathsSetNotificationHandler(datapath, new Callback[Packet] {
            def onSuccess(data: Packet) {
                self ! packetIn(data)
            }

            def onTimeout() {}

            def onError(e: NetlinkException) {}
        }).get()
    }
}
