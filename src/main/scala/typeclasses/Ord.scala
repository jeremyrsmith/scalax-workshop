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

  // This is the base case for induction - HNil is always equal to itself
  implicit val hnilOrd: Ord[HNil] = new Ord[HNil] {
    def compare(a: HNil, b: HNil): Int = 0
  }

  // This is the recursive case - compare the current element, and if they're equal then
  // continue comparing the rest of the elements
  implicit def hlistOrd[H, T <: HList](implicit
    ordH: Ord[H],
    ordT: Ord[T]
  ): Ord[H :: T] = new Ord[H :: T] {
    def compare(a: H :: T, b: H :: T): Int = ordH.compare(a.head, b.head) match {
      case 0   => ordT.compare(a.tail, b.tail)
      case neq => neq
    }
  }

  // this will handle case classes by requiring the induction over the HList
  implicit def genericProductOrd[T <: Product, L <: HList](implicit
    gen: Generic.Aux[T, L],
    ordL: Lazy[Ord[L]]
  ): Ord[T] = new Ord[T] {
    def compare(a: T, b: T): Int = ordL.value.compare(gen.to(a), gen.to(b))
  }

  ////////////////////////////////////////////////////////
  // Coproduct induction - for sealed trait heirarchies //
  ////////////////////////////////////////////////////////

  // the base case for coproduct induction – this instance won't ever be called
  implicit val cnilOrd: Ord[CNil] = new Ord[CNil] {
    def compare(a: CNil, b: CNil): Int = 0
  }

  // this handles case objects (which otherwise wouldn't have an instance)
  implicit def singleton[A <: Singleton](implicit witness: Witness.Aux[A]): Ord[A] = new Ord[A] {
    def compare(a: A, b: A): Int = 0
  }

  // this handles inductive coproducts - if they're the same type, compare them; otherwise
  // the earlier type in the coproduct is smaller (to match Odersky's logic)
  implicit def coproductOrd[H, T <: Coproduct](implicit
    ordH: Ord[H],
    ordT: Ord[T]
  ): Ord[H :+: T] = new Ord[H :+: T] {
    def compare(a: H :+: T, b: H :+: T): Int = (a, b) match {
      case (Inl(ha), Inl(hb)) => ordH.compare(ha, hb)
      case (Inr(ta), Inr(tb)) => ordT.compare(ta, tb)
      case (Inl(_), _) => -1
      case (Inr(_), _) => 1
    }
  }

  // this will handle sealed trait hierarchies by requiring the coproduct induction
  implicit def genericCoproductOrd[T, C <: Coproduct](implicit
    gen: Generic.Aux[T, C],
    ordC: Lazy[Ord[C]]
  ): Ord[T] = new Ord[T] {
    def compare(a: T, b: T): Int = ordC.value.compare(gen.to(a), gen.to(b))
  }

}