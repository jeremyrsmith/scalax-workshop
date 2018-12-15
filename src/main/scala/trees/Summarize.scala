package trees

/**
  * Mutable computation of variance and summary for values of type [[T]]
  */
trait Summary[T] {
  def add(value: T): Unit
  def remove(value: T): Unit
  def variance: Double
  def count: Int
  def mean: Double

  /**
    * @return The summary of values added, i.e. the value with highest probability
    */
  def summary: T

  def copy(): Summary[T]
}

/**
  * Incrementally tracks the mean and variance of a set of Doubles. NaN and infinity are not considered. Summary is
  * the mean of the values.
  */
final class DoubleSummary(
  private var sumSquaredDiffs: Double = 0.0,
  private var currentMean: Double = 0.0,
  private var currentCount: Int = 0
) extends Summary[Double] {

  def count: Int = currentCount
  def mean: Double = currentMean

  override def add(value: Double): Unit = if (!value.isNaN && !value.isInfinite) {
    currentCount += 1
    val delta = value - currentMean
    currentMean += delta / currentCount
    val delta2 = value - currentMean
    sumSquaredDiffs += delta * delta2
  }

  override def remove(value: Double): Unit = if (!value.isNaN && !value.isInfinite){
    currentCount -= 1
    if (currentCount <= 0) {
      currentCount = 0
      currentMean = 0.0
      sumSquaredDiffs = 0.0
    } else {
      val delta = value - currentMean
      currentMean -= delta / currentCount
      val delta2 = value - currentMean
      sumSquaredDiffs -= delta * delta2
    }
  }

  override def variance: Double = if (count > 0) sumSquaredDiffs / count else Double.NaN

  override def summary: Double = mean
  override def copy(): Summary[Double] = new DoubleSummary(sumSquaredDiffs, currentMean, currentCount)
}

final class BooleanSummary(
  private var trueCount: Int = 0,
  private var totalCount: Int = 0
) extends Summary[Boolean] {

  override def add(value: Boolean): Unit = {
    totalCount += 1
    if (value) {
      trueCount += 1
    }
  }

  override def remove(value: Boolean): Unit = {
    totalCount -= 1
    if (value) {
      trueCount -=1
    }
  }

  override def variance: Double = BooleanSummary.variance(trueCount, totalCount)

  override def count: Int = totalCount

  override def mean: Double = trueCount.toDouble / totalCount

  override def summary: Boolean = BooleanSummary.summary(trueCount, totalCount)

  override def copy(): Summary[Boolean] = new BooleanSummary(trueCount, totalCount)
}

object BooleanSummary {
  def variance(trueCount: Int, totalCount: Int): Double = {
    val mean = trueCount.toDouble / totalCount
    mean * (1.0 - mean)
  }

  def summary(trueCount: Int, totalCount: Int): Boolean = trueCount >= (totalCount >> 1)
}

/**
  * Typeclass witnessing that an empty [[Summary]] can be created for type [[T]].
  */
trait Summarize[T] {
  def empty: Summary[T]
}

object Summarize {

  def apply[T](implicit inst: Summarize[T]): Summary[T] = inst.empty

  implicit object SummarizeDouble extends Summarize[Double] {
    override def empty: Summary[Double] = new DoubleSummary()
  }

  implicit object SummarizeBoolean extends Summarize[Boolean] {
    override def empty: Summary[Boolean] = new BooleanSummary()
  }

}
