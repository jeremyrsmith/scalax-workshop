package trees


trait Learner[Features, Target] {
  def learn(depth: Int, data: Array[(Features, Target)]): Features => Target
}

object Learner {

  def apply[Features, Target](depth: Int, data: Array[(Features, Target)])(implicit
    learner: Learner[Features, Target]
  ): Features => Target = learner.learn(depth, data)

  // TODO: derive a Learner
}