package com.dlmaslov.fprocessing.actors

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong
import akka.actor.{Actor, ActorRef, Props, Status}
import akka.http.scaladsl.Http
import akka.routing.RoundRobinPool
import com.dlmaslov.fprocessing.actors.GoogleTranslator.{TranslateFile, TranslateRequest}
import com.dlmaslov.fprocessing.client.HttpClient
import com.dlmaslov.fprocessing.model.Language.Language
import com.dlmaslov.fprocessing.service.CsvFileService
import com.typesafe.scalalogging.StrictLogging
import scala.concurrent.Promise

/**
  * Created by denis on 06.05.17.
  */

object GoogleTranslator extends Named {
  override val name = "GoogleTranslator"

  case class TranslateRequest(inputLang: String, outputLang: String, text: String)
  case class TranslateResponse(text: String)

  case class TranslateFile(filePath: String, inputLang: Language, outputLang: Language)
  val batches = new AtomicLong(0)
}

/**
  * Translator with throttle technique - used bounded BlockingQueue
  * File will be processed via iterator flow to request queue with waiting for slow request sender consumer
  */
class GoogleTranslator(fileService: CsvFileService) extends Translator(fileService) {

  val maxRequests = config.getInt("translator.max-requests")
  val responseDuration = config.getDuration("translator.response-duration").toMillis
  val workers = 50
  val defaultDelay = workers * responseDuration / maxRequests

  val queue: BlockingQueue[RequestFlowContext] = new LinkedBlockingQueue[RequestFlowContext](maxRequests)

  implicit val http = Http(context.system)

  override val requestSender = context.actorOf(
    Props(
      classOf[RequestSender],
      HttpClient.mockTranslate(responseDuration),
      queue,
      defaultDelay,
      maxRequests
    ).withRouter(RoundRobinPool(workers)), RequestSender.name)

  override val responseHandler = context.actorOf(Props(classOf[ResponseHandler]).withRouter(RoundRobinPool(10)), ResponseHandler.name)

  override def sendBatch(flow: FlowContext, flowEnd: Boolean = false): Unit = {
    val requestFlowContext = RequestFlowContext(
      flow.name,
      flow.id.incrementAndGet(),
      uri,
      TranslateRequest(flow.inLang, flow.outLang, flow.batch.toString),
      defaultDelay,
      flowEnd,
      flow.endPromise,
      responseHandler
    )
    val batches = GoogleTranslator.batches.incrementAndGet()
    if (batches % 1000 == 0) {
      val requests = RequestSender.requests.get()
      logger.debug(s"process: " +
        s"batches = ${GoogleTranslator.batches.get()}" +
        s" requests = $requests, ms = ${RequestSender.sendTime.get() / requests}" +
        s" responses = ${ResponseHandler.okResponses.get()}, bad = ${ResponseHandler.badResponses.get()}")
    }
    queue.put(
      requestFlowContext
    )
    flow.batch.clear()
  }
}

/**
  * Flow context for every file translation processing
  */
case class FlowContext(
                        name: String,
                        inLang: Language,
                        outLang: Language,
                        var id: AtomicLong = new AtomicLong(0),
                        batch: StringBuilder = new StringBuilder(),
                        endPromise: Promise[String] = Promise[String]()
                      )

/**
  * Request context for using in Send-Handler flow
  */
case class RequestFlowContext(
                               name: String,
                               id: Long = 0,
                               uri: String,
                               trRequest: TranslateRequest,
                               delay: Long,
                               flowEnd: Boolean,
                               promise: Promise[String],
                               responseHandler: ActorRef
                             )

/**
  * Propagates exceptions to caller for using ask pattern
  */
trait FailurePropagatingActor extends Actor{
  override def preRestart(reason:Throwable, message:Option[Any]){
    super.preRestart(reason, message)
    sender() ! Status.Failure(reason)
  }
}

/**
  * Abstract Translator
  * Implements file processing for translate requests sending
  * No throttle technique
  */
abstract class Translator(fileService: CsvFileService) extends FailurePropagatingActor with StrictLogging {

  val config = context.system.settings.config
  val textColumn = config.getInt("counter.columns.text")
  val uri = config.getString("translator.api-uri")
  val batchSize = config.getInt("translator.batch-max-characters")
  val batchMinFullness = config.getDouble("translator.batch-min-fullness")
  val batchMinSize = (batchSize * batchMinFullness).toInt
  val truncatePhrase = config.getBoolean("translator.truncate-phrases")

  def requestSender: ActorRef

  def responseHandler: ActorRef

  override def receive: Receive = {

    case TranslateFile(path, inLang, outLang) =>
      val origin = sender()
      val flow = flowContext(path, inLang, outLang)

      lines(path)
        .flatMap(row => fileService.parsePhrases(row(textColumn)))
        .foreach { phrase =>
          processPhrase(phrase, flow)
        }
      // send last batch with closing the flow
      sendBatch(flow, flowEnd = true)
      origin ! flow.endPromise.future
  }

  def flowContext(path: String, in: Language, out: Language): FlowContext = FlowContext(path, in, out, new AtomicLong(0), new StringBuilder(batchSize))

  def lines(path: String): Iterator[Seq[String]] = fileService.file(path).lines

  /**
    * Packs phrases in batches; sends completed batches
    */
  def processPhrase(phrase: String, flow: FlowContext): Unit = {
    val phLen = phrase.length
    val btLen = flow.batch.length

    if (phLen > batchSize && !truncatePhrase) {
      // todo to clarify the requirements
      logger.warn(s"Too large to translate: [$phrase]") // can be restore from log if necessary
      flow.batch.append("[...]")
    }
    else if (phLen + btLen <= batchSize) {
      flow.batch.append(phrase)
    }
    else if (truncatePhrase && btLen < batchMinSize) {
      val (headPh, tailPh) = fileService.breakPhrase(phrase, batchSize - btLen)
      flow.batch.append(headPh)
      sendBatch(flow)
      processPhrase(tailPh, flow)
    }
    else {
      sendBatch(flow)
      processPhrase(phrase, flow)
    }
  }

  /**
    * Could be overridden for some throttle techniques
    * Simple implementation. You can use for testing
    */
  def sendBatch(flow: FlowContext, flowEnd: Boolean = false): Unit = {
    requestSender ! RequestFlowContext(
      flow.name,
      flow.id.incrementAndGet(),
      uri,
      TranslateRequest(flow.inLang, flow.outLang, flow.batch.toString),
      0,
      flowEnd,
      flow.endPromise,
      responseHandler
    )
    flow.batch.clear()
  }
}

