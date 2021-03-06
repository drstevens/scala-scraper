package net.ruippeixotog.scalascraper.dsl

import java.io.File

import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{ text => stext, _ }
import net.ruippeixotog.scalascraper.scraper.HtmlExtractor
import net.ruippeixotog.scalascraper.util.Validated.{ VFailure, VSuccess }
import org.jsoup.nodes.Document
import org.specs2.mutable.Specification

class DSLSpec extends Specification {

  "The scraping DSL" should {

    val file = new File(getClass.getClassLoader.getResource("test2.html").toURI)
    val doc = new Browser().parseFile(file)

    "allow trying to extract content which may or may not exist" in {
      doc >?> stext("title") mustEqual Some("Test page")
      doc >?> stext("unknown") mustEqual None
    }

    "support using two extractors at once" in {
      doc >> (stext("title"), stext("#menu .active")) mustEqual ("Test page", "Section 2")
    }

    "support extracting content inside Options and Lists" in {
      Option(doc) >> stext("title") mustEqual Some("Test page")
      Option.empty[Document] >> stext("title") mustEqual None

      List(doc, doc) >> stext("title") mustEqual List("Test page", "Test page")
      List.empty[Document] >> stext("title") mustEqual Nil
    }

    "support chaining element extractors" in {
      doc >> element("#menu") >> stext(".active") mustEqual "Section 2"
      doc >?> element("#menu") >> stext(".active") mustEqual Some("Section 2")
      doc >?> element("#menu") >?> stext(".active") mustEqual Some(Some("Section 2"))
      doc >?> element("unknown") >?> stext(".active") mustEqual None
      doc >> elementList("#menu span") >?> stext("a") mustEqual
        Seq(Some("Home"), Some("Section 1"), None, Some("Section 3"))
    }

    "support mapping over extractors" in {
      val ext = elements("#menu span").map(_.length)
      doc >> ext mustEqual 4
    }

    "support chaining extractors with validators" in {
      doc >?> element("#menu") ~/~ validator("span")(_.nonEmpty) >> stext(".active") mustEqual Some(VSuccess("Section 2"))
      doc >?> element("#menu") ~/~ validator(".active")(_.isEmpty) >> "span" mustEqual Some(VFailure(()))
      doc >?> element("#menu2") ~/~ validator("span")(_.nonEmpty) >> stext(".active") mustEqual None
    }

    "support creating chained extractors as objects for later use" in {
      val ext1 = element("#menu") >?> stext(".active")
      val ext2 = elementList("#menu span") >?> stext("a")
      val ext3 = elementList("#menu span") >?> stext("a") map { _.length }
      val ext4 = elementList("#menu span") >?> stext("a") map { _.flatten.length }

      def useExtractor[A](ext: HtmlExtractor[A]) = doc >> ext

      useExtractor(ext1) mustEqual Some("Section 2")
      useExtractor(ext2) mustEqual Seq(Some("Home"), Some("Section 1"), None, Some("Section 3"))
      useExtractor(ext3) mustEqual 4
      useExtractor(ext4) mustEqual 3
    }

    "support creating chained extractors with validations as objects for later use" in {
      val ext1 = element("#menu") ~/~ validator("span")(_.nonEmpty) >?> stext(".active")
      val ext2 = element("#menu") ~/~ validator(".active")(_.isEmpty) >> "span"

      def useExtractor[A](ext: HtmlExtractor[A]) = doc >> ext

      useExtractor(ext1) mustEqual VSuccess(Some("Section 2"))
      useExtractor(ext2) mustEqual VFailure(())
    }
  }
}
