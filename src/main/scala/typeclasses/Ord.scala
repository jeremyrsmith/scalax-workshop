package typeclasses

/**
  * Another example – to compare to Dotty's new inbuilt typeclass derivation mechanism
  * (as demonstrated by Martin Odersky in his keynote)
  */
trait Ord[T] {
  def compare(a: T, b: T): Int
}

object Ord {

  def apply[T](implicit inst: Ord[T]): Ord[T] = inst

  import shapeless._

  // just so we don't have to define it for leaf types
  implicit def fromOrdering[T](implicit ordering: Ordering[T]): Ord[T] = new Ord[T] {
    def compare(a: T, b: T): Int = ordering.compare(a, b)
  }

  ///////////////////////////////////////////////////
  // HList induction - for products (case classes) //
  ///////////////////////////////////////////////////

  // TODO - we'll fill this in

  ////////////////////////////////////////////////////////
  // Coproduct induction - for sealed trait heirarchies //
  ////////////////////////////////////////////////////////

  // TODO - we'll fill this in

}