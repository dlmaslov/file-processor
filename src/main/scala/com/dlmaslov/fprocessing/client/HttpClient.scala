package com.dlmaslov.fprocessing.client

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.HttpExt
import akka.stream.Materializer
import com.dlmaslov.fprocessing.actors.GoogleTranslator.{TranslateRequest, TranslateResponse}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by denis on 06.05.17.
  */


trait HttpClient {

  def sendRequest(httpRequest: HttpRequest)(implicit mat: Materializer, ec: ExecutionContext): Future[HttpResponse]

}


object HttpClient {

  def simple(implicit http: HttpExt): HttpClient = new SimpleHttpClient()

  def mockTranslate(respDelay: Long) = new MockTranslateHttpClient(respDelay)
}


/**
  * Akka Http Client for sending single requests
  */
class SimpleHttpClient(implicit val http: HttpExt) extends HttpClient with JsonSupport {

  override def sendRequest(httpRequest: HttpRequest)(implicit mat: Materializer, ec: ExecutionContext): Future[HttpResponse] = {
    http.singleRequest(httpRequest)
  }
}

/**
  * Mock Http Client. Commented part for emulating response delay.
  * Don't use default akka system dispatcher!
  */
class MockTranslateHttpClient(respDelay: Long) extends HttpClient with JsonSupport {
//  val ecMock = ExecutionContext.fromExecutor(new ForkJoinPool(100))

  override def sendRequest(httpRequest: HttpRequest)(implicit mat: Materializer, ec: ExecutionContext): Future[HttpResponse] = {
//    implicit val ec1 = ecMock
    for {
      jso <- Unmarshal(httpRequest.entity).to[TranslateRequest]
      rsp <- Marshal(TranslateResponse(s"(FR:)${jso.text}")).to[HttpResponse]
//      _ <- Future{ TimeUnit.MILLISECONDS.sleep(200) }
    } yield {
      rsp
    }
  }
}
