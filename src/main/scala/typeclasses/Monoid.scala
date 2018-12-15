package typeclasses

/**
  * An example typeclass.
  *
  * A Monoid over [[T]] has the capability to:
  * - Combine two T values into single T value
  * - Provide an "identity" T value, which has no effect when combined with any other T value. For example, if the
  *   combine operation is addition, this would be zero; if the combine operation is multiplication, it would be one.
  *
  * Please note that this is just to demonstrate typeclasses and derivation – you probably wouldn't define a single
  * Monoid like this IRL, because it's not specific enough - there could be different monoids for a particular type.
  * We're just pretending we only care about "additive" monoids.
  */
trait Monoid[T] {
  def combine(a: T, b: T): T
  def identity: T
}

/**
  * Monoid's companion object is a good place to put implicit derivations, as well as instances for Scala types.
  * It will be in implicit scope when the compiler searches for Monoid[T] for some T - as will T's companion object.
  * Since we can't add a new implicit value to the companions of types we don't control, this is a good place for them.
  */
object Monoid {

  /**
    * It's good practice to have this "summoner" method, which makes it easy to get the instance from a context bound.
    * For example, we can say:
    *
    * {{
    *     def myFunction[T : Monoid](ts: List[T]): T = ts.foldLeft(Monoid[T].identity) {
    *       (accum, next) => Monoid[T].combine(accum, next)
    *     }
    * }}
    *
    * rather than:
    *
    * {{
    *     def myFunction[T](ts: List[T])(implicit monoidT: Monoid[T]) = ts.foldLeft(monoidT.identity) {
    *       (accum, next) => monoidT.combine(accum, Next)
    *     }
    * }}
    *
    * It's a matter of preference, but using the context bound syntax puts the constraint right up front, rather than
    * burying it in the implicit parameter list. It doesn't hurt to provide this method for people who want to use it.
    */
  def apply[T](implicit inst: Monoid[T]): Monoid[T] = inst

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Some example typeclass instances for Monoid. They can be implicit vals or implicit objects, but in the former //
  // case, make sure to add the type annotation!                                                                   //
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // this instance is an implicit val of an anonymous refinement
  implicit val IntMonoid: Monoid[Int] = new Monoid[Int] {
    final def combine(a: Int, b: Int): Int = a + b
    final def identity: Int = 0
  }

  // this instance is an implicit object which extends Monoid
  implicit object DoubleMonoid extends Monoid[Double] {
    def combine(a: Double, b: Double): Double = a + b
    def identity: Double = 0.0
  }

  // here's an implicit val of a concrete class. The only real advantage over anonymous refinements is the stack trace.
  private final class BooleanMonoid extends Monoid[Boolean] {
    def combine(a: Boolean, b: Boolean): Boolean = a || b
    def identity: Boolean = false
  }

  implicit val BooleanMonoid: Monoid[Boolean] = new BooleanMonoid

  implicit val StringMonoid: Monoid[String] = new Monoid[String] {
    final def combine(a: String, b: String): String = a + b
    val identity: String = ""
  }


  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Here's some example derivations, as seen in the slides. The distinction between a derivation and an implicit  //
  // conversion is that a derivation's arguments are all themselves implicit.                                      //
  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // we're going to mark the tuple derivation with this trait, just to demonstrate in the tests that it gets preferred
  // for tuples over the generic version below
  trait IAmFromTheTupleDerivation

  // Suppose A and B each form monoids. Then it follows that the tuples (A, B) also form a monoid.
  implicit def tupleMonoid[A, B](implicit
    monoidA: Monoid[A],
    monoidB: Monoid[B]
  ): Monoid[(A, B)] = new Monoid[(A, B)] with IAmFromTheTupleDerivation {
    def combine(ab1: (A, B), ab2: (A, B)): (A, B) = (monoidA.combine(ab1._1, ab2._1), monoidB.combine(ab1._2, ab2._2))
    def identity: (A, B) = (monoidA.identity, monoidB.identity)
  }

  // It would sure be a lot of code to write derivations for all shapes of tuples... but it's easy for HLists, because
  // they're shapeless!

  import shapeless._

  implicit val monoidHNil: Monoid[HNil] = new Monoid[HNil] {
    def combine(a: HNil, b: HNil): HNil = HNil
    val identity: HNil = HNil
  }

  implicit def monoidHList[H, T <: HList](implicit
    monoidH: Monoid[H],
    monoidT: Monoid[T]
  ): Monoid[H :: T] = new Monoid[H :: T] {
    def combine(a: H :: T, b: H :: T): H :: T = monoidH.combine(a.head, b.head) :: monoidT.combine(a.tail, b.tail)
    def identity: H :: T = monoidH.identity :: monoidT.identity
  }

  // now we can get all the case classes (including the tuples, which are case classes!) using one more derivation:
  implicit def monoidGeneric[T <: Product, L <: HList](implicit
    gen: Generic.Aux[T, L],
    monoidL: Monoid[L]
  ): Monoid[T] = new Monoid[T] {
    def combine(a: T, b: T): T = gen.from(monoidL.combine(gen.to(a), gen.to(b)))
    def identity: T = gen.from(monoidL.identity)
  }

}
