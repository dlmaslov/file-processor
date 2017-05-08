package com.dlmaslov.fprocessing.actors

import java.util.concurrent.atomic.AtomicLong
import akka.actor.{ActorRef, ActorSystem, Props}
import com.dlmaslov.fprocessing.actors.GoogleTranslator.{TranslateFile, TranslateRequest}
import com.dlmaslov.fprocessing.model.Language
import com.dlmaslov.fprocessing.model.Language.Language
import com.dlmaslov.fprocessing.service.CsvFileService

/**
  * Created by denis on 06.05.17.
  */
object ActorsFixture {
  val mockFileLines = "\"Wonderful, tasty taffy.\"\nIt is very soft and chewy".split("\n").toSeq

  val filePath = "some path"
  val inLang = Language.English
  val outLang = Language.French

  def flowContext = FlowContext(filePath, inLang, outLang, new AtomicLong(0))

  def requestContext(fc: FlowContext, id: Long, uri: String, batchBody: String, flowEnd: Boolean, handler: ActorRef): RequestFlowContext =
    RequestFlowContext(fc.name, id, uri, TranslateRequest(fc.inLang, fc.outLang, batchBody), 0, flowEnd, fc.endPromise, handler)

  val translateFileMessage = TranslateFile(filePath, inLang, outLang)

  def fixtureTranslator(
                         system: ActorSystem,
                         service: CsvFileService,
                         probe: ActorRef,
                         flowContext: FlowContext,
                         truncate: Boolean = true,
                         mockFileLines: Seq[String] = mockFileLines): ActorRef =
    system.actorOf(Props(classOf[FixtureTranslator], service, probe, mockFileLines, flowContext, truncate)/*, name*/)
}

class FixtureTranslator(fileService: CsvFileService, probe: ActorRef, srcLines: Seq[String], flowCxt: FlowContext, truncate: Boolean = true)
  extends Translator(fileService) {

  override val truncatePhrase = truncate
  override val textColumn = 0

  override val requestSender = probe

  override val responseHandler = probe

  override def flowContext(path: String, in: Language, out: Language) = flowCxt

  override def lines(path: String) = {
    srcLines.toIterator.map(fileService.parseLine)
  }

}
