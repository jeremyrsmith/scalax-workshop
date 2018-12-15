package trees
package learn.adapted

import trees.learn.adapted.TypicalTreeLearner._

class TypicalTreeLearner(
  labelCol: Int,
  columnTypes: Seq[ColumnType],
  maxDepth: Int
) {

  /**
    * Find the best split for the given column.
    *
    * Note: A real tree learner would take the column type into account, but we're ignoring it here
    *       to keep things simple.
    */
  private def splitPoint(data: Array[Array[Double]], column: Int, summary: Summary[Double]): Split = {
    val sorted = data.sortBy(arr => arr(column))
    val rightSummary = summary.copy()
    val leftSummary = new DoubleSummary()

    var bestSplitValue = data.head(column)
    var bestVariance = Double.PositiveInfinity
    var bestIndex = 0

    var i = 0
    while (i < data.length) {
      val featureValue = data(i)(column)
      val labelValue = data(i)(labelCol)
      rightSummary.remove(labelValue)
      leftSummary.add(labelValue)

      val avgVariance = (rightSummary.variance + leftSummary.variance) * 0.5
      if (avgVariance < bestVariance) {
        bestSplitValue = featureValue
        bestVariance = avgVariance
        bestIndex = i
      }
      i += 1
    }

    Split(column, bestSplitValue, leftSummary, rightSummary)
  }

  def learn(data: Array[Array[Double]]): DecisionTree[Array[Double], Double] = {
    def impl(data: Array[Array[Double]], summary: Summary[Double], maxDepth: Int): DecisionTree[Array[Double], Double] = {
      if (maxDepth == 0) {
        Leaf.const(summary.summary)
      } else {
        val bestSplit =
          columnTypes.indices.collect {
            case i if i != labelCol => splitPoint(data, i, summary)
          }.minBy(_.overallVariance)

        val (leftData, rightData) = data.partition {
          row => row(bestSplit.column) <= bestSplit.threshold
        }

        val left = impl(leftData, bestSplit.leftSummary, maxDepth - 1)
        val right = impl(rightData, bestSplit.rightSummary, maxDepth - 1)

        val decide = {
          arr: Array[Double] =>
            if (arr(bestSplit.column) <= bestSplit.threshold) left else right
        }

        Branch(decide)
      }
    }

    val overall = new DoubleSummary()
    data foreach {
      row => overall.add(row(labelCol))
    }

    impl(data, overall, maxDepth)
  }

}

object TypicalTreeLearner {
  sealed trait ColumnType
  object ColumnType {
    case object Continuous extends ColumnType
    case object Boolean extends ColumnType
    case object Categorical extends ColumnType
  }

  final case class Split(
    column: Int,
    threshold: Double,
    leftSummary: Summary[Double],
    rightSummary: Summary[Double]) {

    def overallVariance: Double = (leftSummary.variance + rightSummary.variance) * 0.5
  }
}
