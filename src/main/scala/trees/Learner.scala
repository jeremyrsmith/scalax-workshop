package trees


trait Learner[Features, Target] {
  def learn(depth: Int, data: Array[(Features, Target)]): Features => Target
}

object Learner {

  def apply[Features, Target](depth: Int, data: Array[(Features, Target)])(implicit
    learner: Learner[Features, Target]
  ): Features => Target = learner.learn(depth, data)

  implicit def derive[Features, Target : Summarize](implicit
    findSplits: FindSplits.WithLenses[Features, Target]
  ): Learner[Features, Target] = new Learner[Features, Target] {

    private def learnPartition(depth: Int, partition: Partition[Features, Target]): Features => Target = {
      val bestSplit = findSplits(partition).filter(_.branchData.length >= 0).maxBy(_.varianceReduction(partition.summary))
      val defaultValue = partition.summary.summary
      if (depth > 0) {
        val next = bestSplit.branchData.map {
          case branchData if branchData.nonEmpty => learnPartition(depth - 1, branchData)
          case _ => (features: Features) => defaultValue
        }

        features: Features => next(bestSplit.branchIndex(features)).apply(features)
      } else {
        val leafValues = bestSplit.branchData.map(_.summary.summary)
        features: Features => leafValues(bestSplit.branchIndex(features))
      }
    }

    def learn(depth: Int, data: Array[(Features, Target)]): Features => Target = {
      val summary = Summarize[Target]
      data.foreach {
        case (_, target) => summary.add(target)
      }
      learnPartition(depth, Partition(data, summary))
    }

  }


}