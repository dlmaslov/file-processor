package com.dlmaslov.fprocessing.actors

import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicLong
import akka.actor.Actor
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import com.dlmaslov.fprocessing.actors.RequestSender.ConsumeRequest
import com.dlmaslov.fprocessing.client.{HttpClient, JsonSupport}
import com.typesafe.scalalogging.StrictLogging
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Created by denis on 06.05.17.
  */

object RequestSender extends Named {
  override val name = "RequestSender"
  val requests = new AtomicLong(0)
  val sendTime = new AtomicLong(0)

  case object ConsumeRequest
}

/**
  * Http Request Sender with throttle rate is defined with based delay
  *
  * @param client HttpClient
  * @param queue Source queue for requests to send, actor mail box used only for communications between actors
  * @param consumedDelay Delay for getting request from queue to send
  * @param maxOpenedRequests Maximum opened send requests to HttpClient at once time
  */
class RequestSender(client: HttpClient, queue: BlockingQueue[RequestFlowContext], consumedDelay: Long, maxOpenedRequests: Int) extends Actor
   with JsonSupport with StrictLogging {

  implicit val ec: ExecutionContext = context.system.dispatcher
  implicit val materializer = ActorMaterializer()

  val openedRequests = new AtomicLong(0)
  var throttles = 0 // current thread usage
  var throttleThreshold = 1000 // current thread usage


  override def preStart(): Unit = {
    super.preStart()
    scheduleConsume
  }

  override def receive: Receive = {
    case ConsumeRequest =>
      scheduleConsume
      val start: Long = System.currentTimeMillis()
      if (openedRequests.get() < maxOpenedRequests){
        throttles = 0
        val reqCtx = queue.poll()
        if (reqCtx != null) {
          val fResponse = for {
            httpRequest <- createHttpRequest(reqCtx)
            response <- client.sendRequest(httpRequest)
          } yield {
            openedRequests.incrementAndGet()
            RequestSender.requests.incrementAndGet()
            (reqCtx, response)
          }
          fResponse pipeTo reqCtx.responseHandler
          fResponse.onComplete{
            case _ =>
              openedRequests.decrementAndGet()
          }
        }
      } else {
        // throttle
        throttles += 1
        if (throttles >= throttleThreshold){
          // reject requests
          // todo some reject logic
          val _ = queue.poll()
          if (throttles % 100 == 0){
            logger.warn("Throttle reject")
          }
        }
      }
      RequestSender.sendTime.addAndGet(System.currentTimeMillis() - start)
//      scheduleConsume
  }

  def createHttpRequest(ctx: RequestFlowContext)(implicit ec: ExecutionContext): Future[HttpRequest] = {
    for {
      entity <- Marshal(ctx.trRequest).to[RequestEntity]
    } yield {
      HttpRequest(
        method = HttpMethods.POST,
        uri = ctx.uri,
        /* Api Authorization */
        //      headers = List(headers.Authorization(headers.BasicHttpCredentials(someAuthId, someAuthToken)))
        entity = entity
      )
    }

  }

  private def scheduleConsume = {
    context.system.scheduler.scheduleOnce(consumedDelay millis, self, ConsumeRequest)
  }
}
