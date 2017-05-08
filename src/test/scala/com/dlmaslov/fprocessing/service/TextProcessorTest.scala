package com.dlmaslov.fprocessing.service

import org.scalatest.FlatSpec

/**
  * Created by denis on 06.05.17.
  */

class TextProcessorTest extends FlatSpec {

  val processor = new TextProcessor {}

  behavior of "Text Processor"

  it should "parse a sentence to the lowercase words without any delimiters" in {
    val sentence = "My, cats.->^ LOVE - this: \"diet\"?"
    assert(processor.parseWords(sentence).toSeq === Seq("my", "cats", "love", "this", "diet"))
  }

  it should "parse a sentence to phrases with non-character symbols on the end of the phrase" in {
    val sentence = "Wonderful, tasty taffy. This taffy is so good.  It is very soft and chewy"
    val phrases = processor.parsePhrases(sentence)
    assert(phrases === Seq("Wonderful,", " tasty taffy.", " This taffy is so good.", "  It is very soft and chewy."))
  }

  it should "break a phrase from index to left to the closest word" in {
    val sentence = "Wonderful, tasty taffy"
    val (head, tail) = processor.breakPhrase(sentence, 14)
    assert(head === "Wonderful,")
    assert(tail === " tasty taffy")
  }

  it should "break a phrase by index if phrase is a solid word" in {
    val sentence = "WonderfulTastyTaffy"
    val (head, tail) = processor.breakPhrase(sentence, 14)
    assert(head === "WonderfulTasty")
    assert(tail === "Taffy")
  }

}
