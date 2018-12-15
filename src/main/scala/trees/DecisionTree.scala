package trees

/**
  * A decision tree is a function from [[Features]] to a predicted [[Target]]
  */
sealed trait DecisionTree[-Features, Target] extends (Features => Target)

/**
  * A branch is a function which chooses and invokes another [[DecisionTree]] based on the features
  */
final case class Branch[-Features, Target](
  choose: Features => DecisionTree[Features, Target]
) extends DecisionTree[Features, Target] {
  def apply(features: Features): Target = choose(features).apply(features)
}

/**
  * A leaf is a function which returns a predicted target based on the features
  */
final case class Leaf[-Features, Target](
  value: Features => Target
) extends DecisionTree[Features, Target] {
  override def apply(features: Features): Target = value(features)
}

object Leaf {

  /**
    * @return a [[Leaf]] which returns a constant [[Target]]
    */
  def const[Target](value: Target): Leaf[Any, Target] = Leaf(_ => value)
}