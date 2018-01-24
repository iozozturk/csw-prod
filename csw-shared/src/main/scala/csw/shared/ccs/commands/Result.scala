package csw.shared.ccs.commands

import csw.shared.params.generics.{Parameter, ParameterSetKeyData, ParameterSetType}
import csw.shared.params.models.Prefix

/**
 * A parameters set for returning results
 *
 * @param prefix   identifies the target subsystem
 * @param paramSet an optional initial set of parameters (keys with values)
 */
case class Result private (prefix: Prefix, paramSet: Set[Parameter[_]] = Set.empty[Parameter[_]])
    extends ParameterSetType[Result]
    with ParameterSetKeyData {

  override protected def create(data: Set[Parameter[_]]) = new Result(prefix, data)

  // This is here for Java to construct with String
  def this(prefix: String) = this(Prefix(prefix))

}

object Result {
  def apply(prefix: Prefix, paramSet: Set[Parameter[_]] = Set.empty[Parameter[_]]): Result =
    new Result(prefix).madd(paramSet)
}
