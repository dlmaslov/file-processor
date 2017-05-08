package com.dlmaslov.fprocessing.service

import scala.collection.mutable.ArrayBuffer


/**
  * Created by denis on 05.05.17.
  */

case class CsvFormatException(msg: String) extends RuntimeException(msg)

object CsvFormat {
  type Header = Seq[String]
  type Row = Seq[String]

  val emptyHeaderException = CsvFormatException("Can't parse an empty header")
  val emptyFileException = CsvFormatException("Can't parse an empty file")
}

/**
  * Trait for Csv File parsing: to column and lines data sets
  */
trait CsvFormat {
  import CsvFormat._

  def delimiter: Char
  def quote: Char

  /**
    * Splits a line using matching by csv delimiter
    * @throws CsvFormatException if the parameter is empty
    */
  def parseHeader(line: String): Header = {
    if (line.isEmpty) throw emptyHeaderException
    line.split(delimiter)
  }

  def parseLine(line: String): Row = {
    var index = 0
    val len = line.length
    val columns = ArrayBuffer[String]()

    while (index < len){
      val ab = ArrayBuffer[Char]()
      var columnStart = true
      var columnEnd = false
      var quoted = false
      var quotes = 0
      var prevCharacter = '.'
      while (index < len && !columnEnd) {
        val ch = line(index); index += 1
        if (ch == delimiter && (!quoted || quoted && quotes % 2 == 0)) {
            columnEnd = true
        } else if (ch == quote) {
          quotes += 1
          if (columnStart) {
            quoted = true
          }
          if (prevCharacter == quote && quotes % 2 == 1) ab += ch
        } else ab += ch

        prevCharacter = ch
        if (columnStart) columnStart = false
      }
      columns += ab.mkString("")
    }
    columns
  }

  def parseColumn(line: String, columnIndex: Int): String = ??? // TODO Look and parse one column from line
}

/**
  * Processor for text parsing to words and phrases
  */
trait TextProcessor {

  /**
    * Splits a text using matching by non word characters
    */
  def parseWords(text: String): Array[String] = {
    text.toLowerCase.split("\\W+")
  }

  def parsePhrases(text: String): Seq[String] = {

    def endOfPhrase(ch: Char): Boolean = ch == ',' || ch == ';' || ch == '.' || ch == '!' || ch == '?'

    var index = 0
    val len = text.length
    val out = ArrayBuffer[String]()

    while (index < len) {
      val phrase = ArrayBuffer[Char]()
      var phraseEnd = false
      while (index < len && !phraseEnd) {
        val ch = text(index); index += 1
        if (endOfPhrase(ch)){
          phraseEnd = true
        }
        phrase += ch
      }
      if (index >= len) {
        if (!endOfPhrase(phrase(phrase.length - 1))){
          phrase += '.'
        }
      }
      out += phrase.mkString("")
    }
    out
  }

  /**
    * Breaks a phrase from Index to left to the nearest word. If phrase is solid then break by index
    */
  def breakPhrase(phrase: String, maxLen: Int): (String, String) = {
    if (phrase.length <= maxLen){ (phrase, "") }
    else {
      var index = maxLen - 1
      var breakFound = false
      while (index > 0 && !breakFound){
        val ch = phrase(index); index -= 1
        if (ch == ' ') breakFound = true
      }
      if (index == 0) index = maxLen else index += 1
      (phrase.substring(0, index), phrase.substring(index, phrase.length))
    }
  }
}

case class CsvIteratedFile(header: CsvFormat.Header, lines: Iterator[String])


class CsvFileService(override val delimiter: Char = ',', override val quote: Char = '"')
  extends CsvFormat with TextProcessor {
  import CsvFormat._

  def file(path: String, withHeader: Boolean = true): CsvIteratedFile = {
    val it = io.Source.fromFile(path)("UTF-8").getLines
    val header = withHeader match {
      case false => Seq()
      case true =>
        if (it.isEmpty) throw emptyFileException
        parseHeader(it.next())
    }
    CsvIteratedFile(header, it)
  }
}
