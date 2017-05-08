package com.dlmaslov.fprocessing.model

/**
  * Created by denis on 05.05.17.
  */
object Language extends Enumeration {
  type Language = Value

  val English = Value("EN")
  val French = Value("FR")

  implicit def languageToString(v: Language): String = v.toString
}
