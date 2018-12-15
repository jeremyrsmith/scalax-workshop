package trees
package test

import org.scalatest.FreeSpec
import shapeless._
import shapeless.ops.hlist.LiftAll
import shapeless.record.Record

import scala.concurrent.duration.Duration

class TrainingTest extends FreeSpec {

  private val positives = generateData.filter(_._2 == 1.0)
  private val negatives = generateData.filter(_._2 == 0.0)
  private def balancedData(n: Int) = positives.take(n / 2) ++ negatives.take(n / 2)

  private def time[T](fn: => T): (T, Duration) = {
    val start = System.nanoTime()
    val t = fn
    val end = System.nanoTime()
    (t, Duration.fromNanos(end - start))
  }

  "train a tree" in {

    println("Generating training data...")
    val trainingData = balancedData(1000000).toArray

    println("Training tree...")
    val (tree, trainingTime) = time(Learner(8, trainingData))
    println(s"Trained tree in ${trainingTime.toCoarsest}")

    println(s"Generating test data...")
    val results = balancedData(10000).map {
      case (features, label) => tree(features) -> label
    }.toArray

    println("Computing metrics...")
    val err = results.map {
      case (score, label) => label - score
    }

    val avgError = err.sum / err.length
    val maxError = err.max
    val minError = err.min

    val squaredErr = err.map(math.pow(_, 2.0))

    val rmse = math.sqrt(squaredErr.sum / squaredErr.length)

    val binaryAccuracy = results.count {
      case (score, label) => (score > 0.0) == (label > 0.0)
    }.toDouble / results.length

    println(
      s"""Average error:   $avgError
         |Max error:       $maxError
         |RMSE:            $rmse
         |Binary accuracy: $binaryAccuracy""".stripMargin)

  }

}
