/*
 * Copyright 2014 Midokura SARL
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
package org.midonet.cluster

import java.util.UUID

import com.google.inject.Inject
import org.slf4j.{LoggerFactory, Logger}

import org.midonet.cluster.client.HealthMonitorBuilder
import org.midonet.midolman.state.zkManagers.HealthMonitorZkManager
import org.midonet.midolman.state.zkManagers.HealthMonitorZkManager._

/**
 * A cluster data manager for health monitor.
 */
class ClusterHealthMonitorManager extends ClusterManager[HealthMonitorBuilder] {

    private final val log: Logger =
        LoggerFactory.getLogger(classOf[ClusterHealthMonitorManager])

    @Inject
    private val healthMonitorZkManager: HealthMonitorZkManager = null

    private class GetCallBack(private val id: UUID,
                              private val builder: HealthMonitorBuilder)
        extends CallbackWithWatcher[HealthMonitorConfig] {


        protected override def describe(): String = "Health Monitor " + id

        protected override def makeRetry(): Runnable = {
            new Runnable {
                override def run(): Unit = {
                    log.debug("Retrying to get health monitor config {}", id)
                    healthMonitorZkManager.getAsync(id, GetCallBack.this,
                        GetCallBack.this)
                }
            }
        }

        override def onSuccess(cfg: HealthMonitorConfig): Unit = {
            log.debug("Got health monitor config {}", id)

            if (cfg != null) {
                builder.setHealthMonitor(cfg)
                builder.build()
            } else {
                    log.warn("Null health monitor returned from ZK for {}", id)
            }
        }
    }

    override protected def getConfig(id: UUID): Unit = {
        log.debug("health monitor getConfig {}", id)

        getBuilder(id) match {

            case builder: HealthMonitorBuilder =>
                val cb = new GetCallBack(id, builder)
                healthMonitorZkManager.getAsync(id, cb, cb)

            case _ =>
                log.error("Builder not found for Health Monitor {}.", id)
        }

    }
}
