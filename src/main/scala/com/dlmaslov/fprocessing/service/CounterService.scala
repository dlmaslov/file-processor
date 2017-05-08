package com.dlmaslov.fprocessing.service

import com.typesafe.config.Config
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by denis on 05.05.17.
  */
object CounterService {
  type Counter = (String, Int)
}

/**
  * Service for querying and counting information from data file.
  * Used FileService to get the file lines iterator
  */
class CounterService(config: Config, fileService: CsvFileService = new CsvFileService()) {
  import CounterService._

  val maxResultElements = config.getInt("counter.max-result")
  val profileColumn = config.getInt("counter.columns.profile")
  val itemColumn = config.getInt("counter.columns.item")
  val textColumn = config.getInt("counter.columns.text")

  def countTopWords(filePath: String)(implicit ec: ExecutionContext): Future[Seq[Counter]] = {
    Future(
      fileService.file(filePath).lines
        .flatMap(row => fileService.parseWords(row(textColumn)))
        .foldLeft(Map.empty[String, Int])((r, s) => r + (s -> (r.getOrElse(s, 0) + 1)))
        .toSeq.sortBy(-_._2).take(maxResultElements)
        .sortBy(_._1)
    )
  }

  def countTopActiveProfiles(filePath: String)(implicit ec: ExecutionContext): Future[Seq[Counter]] = {
    Future(
      fileService.file(filePath).lines
        .map(row => row(profileColumn))
        .foldLeft(Map.empty[String, Int])((r, s) => r + (s -> (r.getOrElse(s, 0) + 1)))
        .toSeq.sortBy(-_._2).take(maxResultElements)
        .sortBy(_._1)
    )
  }

  def countTopItems(filePath: String)(implicit ec: ExecutionContext): Future[Seq[Counter]] = {
    Future(
      fileService.file(filePath).lines
        .map(row => row(itemColumn))
        .foldLeft(Map.empty[String, Int])((r, s) => r + (s -> (r.getOrElse(s, 0) + 1)))
        .toSeq.sortBy(-_._2).take(maxResultElements)
        .sortBy(_._1)
    )
  }


}
