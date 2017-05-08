package com.dlmaslov.fprocessing.service

import org.scalatest.FlatSpec

/**
  * Created by denis on 06.05.17.
  */
class CsvFormatTest extends FlatSpec {
  val format = new CsvFormat { val delimiter: Char = ','; val quote: Char = '"' }

  behavior of "A CsvFormat"

  it should "read simple header line" in {
    val csv = "Id,ProductId,UserId,ProfileName,HelpfulnessNumerator,HelpfulnessDenominator,Score,Time,Summary,Text"
    val res = format.parseHeader(csv)
    assert(res.size === 10)
  }

  it should "throw CsvFormatException if an empty header is passed" in {
    val csv = ""
    intercept[CsvFormatException]{
      format.parseHeader(csv)
    }
  }

  it should "read quoted and unquoted lines" in {
    val csv =
      """
        |1,222,"A. ""JS"" ",3.4,Ok done. Next
        |2,333, Base team,0.01,"Ok, done... next, one more!"
      """.stripMargin

    val lines = csv.split('\n')

    assert(format.parseLine(lines(1)) === Seq("1","222","A. \"JS\" ","3.4","Ok done. Next"))
    assert(format.parseLine(lines(2)) === Seq("2","333"," Base team","0.01","Ok, done... next, one more!"))
  }

}
