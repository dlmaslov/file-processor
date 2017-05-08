package com.dlmaslov.fprocessing.actors

import java.util.concurrent.atomic.AtomicLong
import akka.actor.Actor
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.dlmaslov.fprocessing.actors.GoogleTranslator.TranslateResponse
import com.dlmaslov.fprocessing.client.JsonSupport
import com.typesafe.scalalogging.StrictLogging
import scala.concurrent.Future

/**
  * Created by denis on 06.05.17.
  */

/**
  * Handler of the responses with translated text
  * Mock. In debug mode can write response to log file. Otherwise only increments the response counters.
  */
object ResponseHandler extends Named {

  override val name = "ResponseHandler"
  val okResponses = new AtomicLong(0)
  val badResponses = new AtomicLong(0)
}

class ResponseHandler extends Actor with JsonSupport with StrictLogging {
  import ResponseHandler._

  implicit val ec = context.system.dispatcher
  implicit val materializer = ActorMaterializer()


  override def receive: Receive = {

    case (ctx: RequestFlowContext, HttpResponse(StatusCodes.OK, headers, entity, _)) =>
      val fHandle = for {
        resp <- Unmarshal(entity).to[TranslateResponse]
      } yield {
        logger.debug(s"Translated [${ctx.trRequest.inputLang}]->[${ctx.trRequest.outputLang}] = ${resp.text}")
        okResponses.incrementAndGet()
      }
      for (e <- fHandle.failed) logger.error(s"Handle response fail: ${e.getMessage}")
      if (ctx.flowEnd) {
        // Mock result with delay for getting others batch responses
        // TODO response handle logic
        // Some notes:
        // Is it necessary:
        // - to handle random responses in original order?
        // - to assign to original review?
        // Batch can contain both part of one review and several different reviews
        Future{
//          TimeUnit.MILLISECONDS.sleep(100)
          ctx.promise.success(s"Mock result (see log): ${ctx.name}-${ctx.trRequest.inputLang}-${ctx.trRequest.outputLang}")
        }
      }

    case (ctx: RequestFlowContext, resp @ HttpResponse(code, _, _, _)) =>
      logger.warn("Request failed, response code: " + code)
      badResponses.incrementAndGet()
      if (ctx.flowEnd) {
        ctx.promise.success(s"Mock result (see log): ${ctx.name}-${ctx.trRequest.inputLang}-${ctx.trRequest.outputLang}")
      }
      if (ctx.flowEnd) {
        // TODO failed response handle logic
        // Some notes:
        // - Is it necessary to resend or backlog fail request?
        // - Is it necessary to break all stream when fail occurs?
        ctx.promise.failure(new RuntimeException(s"Translation fail, response code = $code, ctx = $ctx"))
      }
      resp.discardEntityBytes()

  }
}
