package typeclasses

/**
  * An example type function. The result of adding type A and type B is type Result.
  */
trait Add[A, B] {
  type Result
  def apply(a: A, b: B): Result
}

object Add {

  implicit object AddInts extends Add[Int, Int] {
    type Result = Int
    def apply(a: Int, b: Int): Int = a + b
  }

  implicit object AddIntDouble extends Add[Int, Double] {
    type Result = Double
    def apply(a: Int, b: Double): Double = a + b
  }

}