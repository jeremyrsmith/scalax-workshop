package trees.util

import shapeless._
import org.scalatest.{FreeSpec, Matchers}

class LensesSpec extends FreeSpec with Matchers {

  import LensesSpec.Example
  val example = Example(1, "two", true, 22.22)

  "Lenses" - {
    "creates an HList of functions from Example to each field of Example" in {

      val lenses = Lenses[Example]

      lenses.lenses match {
        case first :: second :: crazy :: capital :: HNil =>
          first(example) shouldEqual example.first
          second(example) shouldEqual example.second
          crazy(example) shouldEqual example.`crazy name`
          capital(example) shouldEqual example.CapitalName
      }

    }
  }

  "Getter" - {
    "creates a single getter for the specified key" in {
      val getter = implicitly[Getter.Of[Example, Witness.`'second`.T]]

      getter(example) shouldEqual example.second
    }
  }

}

object LensesSpec {

  final case class Example(
    first: Int,
    second: String,
    `crazy name`: Boolean,
    CapitalName: Double
  )

}
