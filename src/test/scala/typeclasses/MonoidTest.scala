package typeclasses

import org.scalactic.Equality
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit._

class MonoidTest extends FreeSpec with Matchers {

  // in order to invoke this method, the compiler must find an implicit Monoid[A]. Otherwise, it's a compile error.
  def test[A : Monoid : Equality](expected: A)(first: A, second: A): Unit = {
    Monoid[A].combine(first, second) shouldEqual expected
  }

  "Instances" - {
    // Just to show that those instances are available without importing - since they're in implicit scope

    "int" in {
      test(25)(10, 15)
    }

    "double" in {
      test(25.0)(10.0, 15.0)
    }

    "boolean" in {
      test(true)(true, true)
      test(true)(true, false)
      test(true)(false, true)
      test(false)(false, false)
    }

  }

  "Derivations" - {
    // To show that the derivations we specified will also be found by implicit search, and used automatically

    "2-tuple" in {
      // Derived for (Int, Double)
      test((8, 32.0))((4, 16.0), (4, 16.0))

      // Since it's a more speficic derivation (directly targets the tuple type), this should be supplied by the
      // tuple2 derivation
      assert(Monoid[(Int, Double)].isInstanceOf[Monoid.IAmFromTheTupleDerivation])

      // even if the author of Monoid didn't think about our type, we can still use it in derivations by supplying
      // our own instance

      implicit val durationMonoid: Monoid[Duration] = new Monoid[Duration] {
        def identity: Duration = Duration.Zero
        def combine(a: Duration, b: Duration): Duration = a + b
      }

      test(("hellogoodbye", Duration("3d")))(
        ("hello", Duration(1, DAYS)), ("goodbye", Duration(2, DAYS))
      )
    }

    "3-tuple" in {
      // We didn't define a 3-tuple derivation - but it's handled by the Generic derivation via HLists!
      test(("abcd", true, 45))(("ab", false, 30), ("cd", true, 15))
    }

    "10-tuple" in {
      // Just to drive home that point...
      test(("abcd", true, 45, 11.0, false, "wizzlewozzle", 100, true, 42.0, 1000))(
        ("ab", false, 30, 5.0, false, "wizzle", 55, true, 21.0, 999), ("cd", true, 15, 6.0, false, "wozzle", 45, false, 21.0, 1)
      )
    }

    "case classes" in {

      // we can even do it for any case class!

      case class Foo(bar: String, baz: Int, buzz: Double)

      test(Foo("foobar", 22, 64.0))(Foo("foo", 10, 32.0), Foo("bar", 12, 32.0))

    }

  }

}
