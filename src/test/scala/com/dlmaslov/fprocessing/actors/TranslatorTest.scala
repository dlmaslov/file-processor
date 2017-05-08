package com.dlmaslov.fprocessing.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import com.dlmaslov.fprocessing.service.CsvFileService
import com.typesafe.config.ConfigFactory
import org.scalatest._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by denis on 06.05.17.
  */

class TranslatorTest extends TestKit(ActorSystem("test-translator", ConfigFactory.load("test.conf")))
  with WordSpecLike with BeforeAndAfterAll {
  import ActorsFixture._

  val service = new CsvFileService()

  val uri = system.settings.config.getString("translator.api-uri")

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "Translator" should {
    "eventually split a file lines to translate batch requests" in {

      val probe = TestProbe()
      val fc = flowContext
      val translator = fixtureTranslator(system, service, probe, fc)

      val batch1 = requestContext(fc, 1, uri, "Wonderful, tasty taffy.", flowEnd = false, probe)
      val batch2 = requestContext(fc, 2, uri, "It is very soft and", flowEnd = false, probe)
      val batch3 = requestContext(fc, 3, uri, " chewy.", flowEnd = true, probe)

      within(1 seconds) {
        translator ! translateFileMessage
        probe.expectMsg[RequestFlowContext](batch1)
        probe.expectMsg[RequestFlowContext](batch2)
        probe.expectMsg[RequestFlowContext](batch3)
      }
    }

    "cut non truncatable phrases" in {

      val probe = TestProbe()
      val fc = flowContext
      val translator = fixtureTranslator(system, service, probe, fc, truncate = false)

      val batch1 = requestContext(fc, 1, uri, "Wonderful, tasty taffy.[...]", flowEnd = true, probe)

      within(1 seconds) {
        translator ! translateFileMessage
        probe.expectMsg[RequestFlowContext](batch1)
      }
    }
  }

  private implicit def testProbeToActorRef(p: TestProbe): ActorRef = p.testActor

}
