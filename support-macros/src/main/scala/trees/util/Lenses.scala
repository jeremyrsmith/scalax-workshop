package trees.util

import shapeless.{CaseClassMacros, DepFn0, HList, SingletonTypeUtils}

import scala.reflect.macros.whitebox

/**
  * Typeclass providing an HList of lenses into the fields of case class [[A]], in linearization order (the same order as [[shapeless.Generic]]).
  */
trait Lenses[A] extends DepFn0 {
  type Out <: HList
  def lenses: Out
  override final def apply(): Out = lenses
}

object Lenses {
  type Aux[A, Out0 <: HList] = Lenses[A] { type Out = Out0 }

  def apply[A](implicit lenses: Lenses[A]): Aux[A, lenses.Out] = lenses

  implicit def derive[A, Out]: Aux[A, Out] = macro LensesMacros.derive[A, Out]

}

/**
  * Typeclass providing a getter of type Out from type A
  */
trait Getter[A] {
  @specialized type Out
  @specialized def apply(a: A): Out
}

object Getter {

  trait Of[A, K] extends Getter[A]
  type AuxOf[A, K, Out0] = Getter.Of[A, K] { type Out = Out0 }
  type Aux[A, Out0] = Getter[A] { type Out = Out0 }

  implicit def of[A, K, Out]: AuxOf[A, K, Out] = macro LensesMacros.of[A, K, Out]

}

class LensesMacros(val c: whitebox.Context) extends CaseClassMacros with SingletonTypeUtils {

  import c.universe._

  private lazy val Function1TypeConstructor = weakTypeOf[Function1[_, _]].typeConstructor
  private def Function1(T1: Type, R: Type): Type = appliedType(Function1TypeConstructor, T1, R)

  def derive[A : WeakTypeTag, Out : WeakTypeTag]: Tree = {
    val A = weakTypeOf[A].dealias.widen

    fieldsOf(A) match {
      case Nil    => c.abort(c.enclosingPosition, s"$A is not a case class, or has no fields")
      case fields =>

        val (fieldLensFns, fieldLensTypes) = fields.map {
          case (name, typ) =>
            val description = s"_.${name.decodedName.toString}"
            val fn = q"""
               new Function1[$A, $typ] {
                 def apply(a: $A): $typ = a.$name
                 override def toString(): String = $description
               }
             """

            (fn, Function1(A, typ))
        }.unzip

        val fieldLensesHListType = mkHListTpe(fieldLensTypes)
        val fieldLensesHListTree = mkHListValue(fieldLensFns)

        val lensesType = appliedType(weakTypeOf[Lenses[_]].typeConstructor, A)
        val auxType = appliedType(weakTypeOf[Lenses.Aux[_, _]].typeConstructor, A, fieldLensesHListType)

        q"""
           new $lensesType {
             final type Out = $fieldLensesHListType
             final val lenses: $fieldLensesHListType = $fieldLensesHListTree
           }: $auxType
         """
    }
  }

  def of[A : WeakTypeTag, K : WeakTypeTag, Out : WeakTypeTag]: Tree = {
    val A = weakTypeOf[A].dealias.widen
    val K = weakTypeOf[K].dealias

    val fieldName = K match {
      case SingletonSymbolType(field) => field
      case ConstantType(Constant(field: String)) => field
      case _ => c.abort(c.enclosingPosition, s"$K is not a constant string or symbol type")
    }

    val fieldTerm = TermName(fieldName)
    val field = A.member(fieldTerm).asMethod

    if (!field.isGetter)
      c.abort(c.enclosingPosition, s"$K refers to a field or method which is not a getter")

    val Out = field.asMethod.infoIn(A) match {
      case MethodType(Nil, resultType) => resultType
      case MethodType(_, _) => c.abort(c.enclosingPosition, s"$K refers to a method which takes arguments")
      case typ => typ.finalResultType // Don't think this should occur
    }

    val getterType = appliedType(weakTypeOf[Getter.Of[_, _]].typeConstructor, A, K)
    val auxType = appliedType(weakTypeOf[Getter.AuxOf[_, _, _]].typeConstructor, A, K, Out)

    val description = s"${A.typeSymbol.name.toString}#$fieldName"
    q"""
       new $getterType {
         type Out = $Out
         final def apply(a: $A): $Out = a.$fieldTerm
         override final def toString(): String = $description
       }
     """
  }

}