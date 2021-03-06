package csw.apps.clusterseed.cli

import java.io.ByteArrayOutputStream

import org.scalatest.{FunSuite, Matchers}

class ArgsParserTest extends FunSuite with Matchers {

  //Capture output/error generated by the parser, for cleaner test output. If interested, errCapture.toString will return capture errors.
  val outCapture = new ByteArrayOutputStream
  val errCapture = new ByteArrayOutputStream
  val parser     = new ArgsParser

  def silentParse(args: Array[String]): Option[Options] =
    Console.withOut(outCapture) {
      Console.withErr(errCapture) {
        parser.parse(args)
      }
    }

  test("parse without arguments") {
    val args = Array("")
    silentParse(args) shouldBe None
  }

  test("parse with all arguments") {
    val args = Array("--clusterPort", "1234", "--adminPort", "5678")
    silentParse(args) shouldBe Some(Options(1234, Some(5678)))
  }

  test("parse with some arguments") {
    val args = Array("--clusterPort", "1234")
    silentParse(args) shouldBe Some(Options(1234))
  }
}
