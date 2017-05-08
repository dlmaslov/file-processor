package com.dlmaslov.fprocessing

import java.util.concurrent.ForkJoinPool

import akka.actor.{ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.dlmaslov.fprocessing.actors.{GoogleTranslator, RequestSender, ResponseHandler}
import com.dlmaslov.fprocessing.model.Language
import com.dlmaslov.fprocessing.service.{CounterService, CsvFileService}
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Created by denis on 05.05.17.
  */
object Main extends App {

  val (filePath, translate) = arguments(args)
  val config = ConfigFactory.load().withFallback(ConfigFactory.load("file-processor.conf"))
  val fileService = new CsvFileService()

  if (translate) {
    implicit val system = ActorSystem("AppFileProcessor", config)
    implicit val ec = system.dispatcher

    val resultDelay = 15 minutes
    implicit val timeout = Timeout(resultDelay)

    val translator = system.actorOf(Props(classOf[GoogleTranslator], fileService), GoogleTranslator.name)

    val fTranslate = for {
      prom <- (translator ? GoogleTranslator.TranslateFile(filePath, Language.English, Language.French)).mapTo[Future[String]]
      res <- prom
    } yield {
      println(s"Translation result: $res")
    }

    fTranslate.onComplete {
      case Failure(e) =>
        println(s"Translation error: $e")
        system.terminate()

      case Success(_) =>
        println(s"Translate successful: " +
          s"batches = ${GoogleTranslator.batches.get()} " +
          s"requests = ${RequestSender.requests.get()} " +
          s"responses = ${ResponseHandler.okResponses.get()}, bad = ${ResponseHandler.badResponses.get()}")
    }

    Await.result(fTranslate, resultDelay)
    system.terminate()

  } else {
    implicit val ec = ExecutionContext.fromExecutor(new ForkJoinPool(sys.runtime.availableProcessors()))
    val service = new CounterService(config, fileService)

    val fUsers = service.countTopActiveProfiles(filePath)
    val fItems = service.countTopItems(filePath)
    val fWords = service.countTopWords(filePath)

    val result = for {
      users <- fUsers
      items <- fItems
      words <- fWords
    } yield {
      println("\nTop active profiles"); users.foreach(c => println(c))
      println("\nTop commented items"); items.foreach(c => println(c))
      println("\nTop review words"); words.foreach(c => println(c))
    }
    result.onComplete{
      case Success(_) => println("File processing is done")
      case Failure(e) => println(s"File processing fail: $e")
    }

    Await.result(result, 10 minutes)
  }


  def arguments(args: Array[String]): (String, Boolean) = {
    args.toList match {
      case f :: "--translate" :: t :: Nil => (f, t.toBoolean)
      case f :: Nil => (f, false)
      case _ =>
        println("Prompt: filename [--translate true]")
        sys.exit()
    }
  }

}
