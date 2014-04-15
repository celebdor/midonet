/**
 * Copyright (c) 2014 Midokura Europe SARL, All Rights Reserved.
 */
package org.midonet.midolman.util

import scala.collection.JavaConversions._

import com.google.inject._
import org.apache.commons.configuration.HierarchicalConfiguration
import org.scalatest.BeforeAndAfter
import org.scalatest.FeatureSpecLike
import org.scalatest.GivenWhenThen
import org.scalatest.Matchers
import org.scalatest.OneInstancePerTest

import org.midonet.cluster.services.MidostoreSetupService
import org.midonet.midolman.util.mock.MockMidolmanActors
import org.midonet.midolman.services.MidolmanService
import org.midonet.midolman.simulation.CustomMatchers

/**
 * A base trait to be used for new style Midolman simulation tests with Midolman
 * Actors.
 */
trait MidolmanSpec extends FeatureSpecLike
        with VirtualConfigurationBuilders
        with Matchers
        with BeforeAndAfter
        with GivenWhenThen
        with CustomMatchers
        with MockMidolmanActors
        with MidolmanServices
        with VirtualTopologyHelper
        with OneInstancePerTest {
    var injector: Injector = null

    /**
     * Override this function to perform a custom set-up needed for the test.
     */
    protected def beforeTest() { }

    /**
     * Override this function to perform a custom shut-down operations needed
     * for the test.
     */
    protected def afterTest() { }

    before {
        try {
            val config = fillConfig(new HierarchicalConfiguration)
            injector = Guice.createInjector(getModules(config))

            actorsService.register(registerActors)

            injector.getInstance(classOf[MidostoreSetupService]).startAndWait()
            injector.getInstance(classOf[MidolmanService]).startAndWait()

            beforeTest()
        } catch {
            case e: Throwable => fail(e)
        }
    }

    after {
        afterTest()
        actorSystem.shutdown()
    }

    protected def fillConfig(config: HierarchicalConfiguration)
    : HierarchicalConfiguration = {
        config.setProperty("midolman.midolman_root_key", "/test/v3/midolman")
        config.setProperty("midolman.enable_monitoring", "false")
        config.setProperty("cassandra.servers", "localhost:9171")
        config
    }
}