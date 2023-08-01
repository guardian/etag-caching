package com.gu.etagcaching.aws.sdkv2.s3

import org.w3c.dom.Document

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

case class Fruit(name: String, colour: String)

/**
 * This is just an example of a parser for tests, using XML because there are some classes in Java
 * that we could use without introducing another dependency. For your production code, you may well
 * be using JSON, Thrift, Avro, etc!
 */
object ExampleParser {
  val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()

  def parseXml(is: InputStream): Document = documentBuilderFactory.newDocumentBuilder().parse(is)

  def parseFruit(is: InputStream): Fruit = {
    val elem = parseXml(is).getDocumentElement
    Fruit(elem.getAttribute("name"), elem.getAttribute("colour"))
  }
}
