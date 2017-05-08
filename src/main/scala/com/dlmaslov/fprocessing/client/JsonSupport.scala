package com.dlmaslov.fprocessing.client

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.dlmaslov.fprocessing.actors.GoogleTranslator.{TranslateRequest, TranslateResponse}
import spray.json.DefaultJsonProtocol

/**
  * Created by denis on 06.05.17.
  */


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val translateRequestFormat = jsonFormat3(TranslateRequest)
  implicit val translateResponseFormat = jsonFormat1(TranslateResponse)

}
