package csw.shared.params.models

import java.util

import scalapb.TypeMapper
import csw.shared.params.pb.{ItemType, ItemTypeCompanion}
import play.api.libs.json.{Format, Json}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * A top level key for a parameter set representing an array like collection.
 *
 * @param data input array
 */
case class ArrayData[T](data: mutable.WrappedArray[T]) {
  //scala
  def values: Array[T] = data.array
  //java
  def jValues: util.List[T] = data.asJava

  override def toString: String = data.mkString("(", ",", ")")
}

object ArrayData {
  implicit def format[T: Format: ClassTag]: Format[ArrayData[T]] = Json.format[ArrayData[T]]

  implicit def fromArray[T](xs: Array[T]): ArrayData[T] = new ArrayData(xs)

  //scala
  def fromArray[T: ClassTag](xs: T*): ArrayData[T] = new ArrayData(xs.toArray[T])
  //A Java helper to instantiate ArrayData
  def fromJavaArray[T](array: Array[T]): ArrayData[T] = ArrayData.fromArray(array)

  implicit def typeMapper[T: ClassTag, S <: ItemType[T]: ItemTypeCompanion]: TypeMapper[S, ArrayData[T]] =
    TypeMapper[S, ArrayData[T]](x ⇒ ArrayData(x.values.toArray[T]))(x ⇒ ItemTypeCompanion.make(x.data))

  implicit def conversion[A, B](implicit conversion: A ⇒ B): ArrayData[A] ⇒ ArrayData[B] = _.asInstanceOf[ArrayData[B]]
}
