package trees

import shapeless.ops.coproduct.Reify
import shapeless.ops.hlist.{At, Mapper, ToList, Zip}
import shapeless._
import shapeless.ops.nat.ToInt
import trees.util.Lenses

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

final case class Partition[A, Target](data: Array[(A, Target)], summary: Summary[Target]) {
  def nonEmpty: Boolean = data.nonEmpty
}

sealed trait Split[A, Target] {
  def branchIndex: A => Int
  def branchData: Array[Partition[A, Target]]
  def varianceReduction(original: Summary[Target]): Double = {
    val nonEmptyVariances = branchData.collect {
      case part if part.nonEmpty => part.summary.variance
    }
    if (nonEmptyVariances.isEmpty) 0.0 else nonEmptyVariances.sum / nonEmptyVariances.length
  }
}

object Split {
  def apply[A, Target](branchIndex: A => Int, branches: Array[Partition[A, Target]]): Split[A, Target] = {
    branches.filter(_.nonEmpty) match {
      case Array() => throw new IllegalArgumentException("All branches are empty, so something's gone wrong!")
      case Array(single) => LeafSplit(single)
      case arr => BranchSplit(branchIndex, branches)
    }
  }
}

final case class BooleanSplit[A, Target](
  lens: A => Boolean,
  leftF: Partition[A, Target],
  rightT: Partition[A, Target]
) extends Split[A, Target] {
  val branchIndex: A => Int = a => if (lens(a)) 1 else 0
  val branchData: Array[Partition[A, Target]] = Array(leftF, rightT)
}

final case class DoubleSplit[A, Target](
  lens: A => Double,
  threshold: Double,
  left: Partition[A, Target],
  right: Partition[A, Target]
) extends Split[A, Target] {
  val branchIndex: A => Int = a => if (lens(a) <= threshold) 0 else 1
  val branchData: Array[Partition[A, Target]] = Array(left, right)
}

final case class BranchSplit[A, Target](
  branchIndex: A => Int,
  branchData: Array[Partition[A, Target]]
) extends Split[A, Target]

final case class LeafSplit[A, Target](
  partition: Partition[A, Target]
) extends Split[A, Target] {
  override val branchIndex: A => Int = _ => 0
  override val branchData: Array[Partition[A, Target]] = Array(partition)
  override def varianceReduction(original: Summary[Target]): Double = 0.0
}

/**
  * Typeclass supporting finding the optimal split for data type [[T]], given an array of values labeled with [[Target]]
  */
trait FindSplit[T, Target] {

  /**
    * Given an array of labelled values (a tuple of feature values with a target ground truth value), find the
    * optimal split for a particular feature accessed with the lens function
    */
  def apply[A](lens: A => T): Partition[A, Target] => Split[A, Target]

}

trait LowPriorityFindSplit {
  /**
    * To find the best split for Double feature values, we have to sort the dataset by the target feature first.
    */
  implicit def double[Target : Summarize : ClassTag]: FindSplit[Double, Target] = new FindSplit[Double, Target] {
    override final def apply[A](lens: A => Double): Partition[A, Target] => Split[A, Target] = { case Partition(data, summary) =>

      require(data.length > 0, "Dataset must not be empty")

      // track the variance on both sides of the split
      val leftSummary = Summarize[Target]
      val rightSummary = summary.copy()

      // sort by the feature value
      val sorted = data.sortBy {
        case (features, target) => lens(features)
      }

      // track the best variance reduction and the feature value that led to it
      var bestVarianceReduction = 0.0
      var bestValue = lens(sorted.head._1)
      var bestIndex = 0
      var bestLeftSummary = leftSummary.copy()
      var bestRightSummary = rightSummary.copy()

      // zip through the sorted dataset, moving each target from the right side to the left and checking the
      // variance reduction (overall variance - average of left and right variances) at each step
      sorted.zipWithIndex.foreach {
        case ((features, target), i) =>
          rightSummary.remove(target)
          leftSummary.add(target)

          val varianceReduction = summary.variance - ((rightSummary.variance + leftSummary.variance) * 0.5)
          if (varianceReduction > bestVarianceReduction) {
            bestVarianceReduction = varianceReduction
            bestValue = lens(features)
            bestIndex = i
            bestLeftSummary = leftSummary.copy()
            bestRightSummary = rightSummary.copy()
          }
      }

      val (leftData, rightData) = sorted.splitAt(bestIndex)

      if (leftData.isEmpty || rightData.isEmpty)
        LeafSplit(Partition(data, summary))
      else
        DoubleSplit(lens, bestValue, Partition(leftData, bestLeftSummary), Partition(rightData, bestRightSummary))
    }
  }

  /**
    * For other numeric types, we just convert to double
    */
  implicit def numeric[T : Numeric, Target](implicit
    splitDouble: FindSplit[Double, Target]
  ): FindSplit[T, Target] = new FindSplit[T, Target] {
    override def apply[A](lens: A => T): Partition[A, Target] => Split[A, Target] = splitDouble(lens andThen (t => implicitly[Numeric[T]].toDouble(t)))
  }

  /**
    * For Boolean feature values, there's no need to sort - there's only one possible split, we just have to find
    * the variance reduction for it.
    */
  implicit def boolean[Target : Summarize : ClassTag]: FindSplit[Boolean, Target] = new FindSplit[Boolean, Target] {
    override final def apply[A](lens: A => Boolean): Partition[A, Target] => Split[A, Target] = { case Partition(data, summary) =>

      require(data.length > 0, "Dataset must not be empty")

      val leftSummary = Summarize[Target]
      val rightSummary = Summarize[Target]

      // split into left (false) and right (true) values
      val (right, left) = data.partition {
        case (a, _) => lens(a)
      }

      if (left.isEmpty || right.isEmpty) {
        LeafSplit(Partition(data, summary))
      } else {
        right.foreach {
          case (_, target) => rightSummary.add(target)
        }

        left.foreach {
          case (_, target) => leftSummary.add(target)
        }

        BooleanSplit(lens, Partition(left, leftSummary), Partition(right, rightSummary))
      }
    }
  }

  /**
    * For a categorical variable modeled by a sealed trait of N case objects, there's also no need to sort - the
    * easiest thing to do is split into N branches by category
    */
  implicit def categorical[T, Target : Summarize : ClassTag, C <: Coproduct, L <: HList](implicit
    gen: Generic.Aux[T, C],  // The possible case objects as a Coproduct
    reify: Reify.Aux[C, L],  // Provides an HList of the case objects
    toList: ToList[L, T]     // Convert the HList to an ordinary List, typed to T
  ): FindSplit[T, Target] = new FindSplit[T, Target] {

    // build a mapping of case object to integer, based on its index in the coproduct
    private val indexMap = toList(reify()).zipWithIndex.toMap

    override final def apply[A](lens: A => T): Partition[A, Target] => Split[A, Target] = { case Partition(data, summary) =>

      require(data.length > 0, "Dataset must not be empty")

      // allocate partitions for each category
      val partData = Array.fill(indexMap.size + 1)(new ArrayBuffer[(A, Target)])
      val summaries = Array.fill(indexMap.size)(Summarize[Target])

      data.foreach {
        case tuple @ (a, target) =>
          val index = indexMap(lens(a))
          partData(index) += tuple
          summaries(index).add(target)
      }

      val partitions = partData.zip(summaries).map {
        case (d, s) => Partition(data, summary)
      }

      Split(x => indexMap.getOrElse(lens(x), 0), partitions)
    }
  }

}


object FindSplit extends LowPriorityFindSplit {
  /**
    * When both the feature value and the targets are Booleans, the data only needs to be iterated once – we can partition
    * into left and right datasets and compute the variances simultaneously
    */
  implicit val booleanWithBooleanTargets: FindSplit[Boolean, Boolean] = new FindSplit[Boolean, Boolean] {
    override final def apply[A](lens: A => Boolean): Partition[A, Boolean] => Split[A, Boolean] = { case Partition(data, summary) =>

      require(data.length > 0, "Dataset must not be empty")

      // allocate buffers for the left and right partitions
      val left = new ArrayBuffer[(A, Boolean)]()
      val right = new ArrayBuffer[(A, Boolean)]()

      // counters for computing the variance
      var leftTrue = 0
      var rightTrue = 0

      // partition based on the feature value while counting the number of positive targets in each partition
      data.foreach {
        case tuple @ (a, target) =>
          if (lens(a)) {
            right += tuple
            if (target) {
              rightTrue += 1
            }
          } else {
            left += tuple
            if (target) {
              leftTrue += 1
            }
          }
      }

      // compute variance reduction
      val partitions = Array(
        Partition(left.toArray, new BooleanSummary(leftTrue, left.size)),
        Partition(right.toArray, new BooleanSummary(rightTrue, right.size))
      )

      Split(x => if (lens(x)) 1 else 0, partitions)
    }
  }
}

trait FindSplits[A, Target] {
  type Out <: HList
  def apply(): Out
}

object FindSplits {

  type Aux[A, Target, Out0 <: HList] = FindSplits[A, Target] { type Out = Out0 }

  final case class Instance[A, Target, Out0 <: HList](value: Out0) extends FindSplits[A, Target] {
    type Out = Out0
    def apply(): Out = value
  }

  implicit def hnil[Target]: Aux[HNil, Target, HNil] = Instance(HNil)

  implicit def hcons[H, T <: HList, Target, TOut <: HList](implicit
    findSplit: FindSplit[H, Target],
    findSplitsT: FindSplits.Aux[T, Target, TOut]
  ): Aux[H :: T, Target, FindSplit[H, Target] :: TOut] = Instance(findSplit :: findSplitsT.apply)

  implicit def generic[A <: Product, Target, L <: HList, Out <: HList](implicit
    gen: Generic.Aux[A, L],
    findSplitsL: Aux[L, Target, Out]
  ): Aux[A, Target, Out] = Instance(findSplitsL())

  trait WithLenses[A, Target] extends (Partition[A, Target] => List[Split[A, Target]])

  object WithLenses {

    object applyLenses extends Poly1 {
      implicit def cases[T, A, Target] = at[(FindSplit[T, Target], A => T)] {
        case (findSplit, extractT) => findSplit(extractT)
      }
    }

    implicit def generic[A <: Product, Target, Fs <: HList, Ls <: HList, Z <: HList, Ap <: HList](implicit
      findSplits: FindSplits.Aux[A, Target, Fs],
      lenses: Lenses.Aux[A, Ls],
      zip: Zip.Aux[Fs :: Ls :: HNil, Z],
      mapper: Mapper.Aux[applyLenses.type, Z, Ap],
      toList: ToList[Ap, Partition[A, Target] => Split[A, Target]]
    ): WithLenses[A, Target] = {
      val splitFinders = (findSplits() :: lenses() :: HNil).zip.map(applyLenses).toList

      data => splitFinders.map(_.apply(data))
    }

  }


}