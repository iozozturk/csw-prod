package csw.shared.params.generics

import csw.shared.ccs.commands.{Observe, SequenceCommand, Setup, Wait}
import csw.shared.params.models.Subsystem

/**
 * A collection of Utility functions for filtering Commands and Parameters from an input sequence.
 */
object ParameterSetFilters {
  // A filter type for various parameter set data
  type ParamSetFilter[A] = A => Boolean

  def prefixes(paramSets: Seq[ParameterSetKeyData]): Set[String] = paramSets.map(_.prefixStr).toSet

  def onlySetups(paramSets: Seq[SequenceCommand]): Seq[Setup] = paramSets.collect { case ct: Setup => ct }

  def onlyObserves(paramSets: Seq[SequenceCommand]): Seq[Observe] = paramSets.collect { case ct: Observe => ct }

  def onlyWaits(paramSets: Seq[SequenceCommand]): Seq[Wait] = paramSets.collect { case ct: Wait => ct }

  val prefixStartsWithFilter: String => ParamSetFilter[ParameterSetKeyData] = query => sc => sc.prefixStr.startsWith(query)
  val prefixContainsFilter: String => ParamSetFilter[ParameterSetKeyData]   = query => sc => sc.prefixStr.contains(query)
  val prefixIsSubsystem: Subsystem => ParamSetFilter[ParameterSetKeyData]   = query => sc => sc.subsystem.equals(query)

  def prefixStartsWith(query: String, paramSets: Seq[ParameterSetKeyData]): Seq[ParameterSetKeyData] =
    paramSets.filter(prefixStartsWithFilter(query))

  def prefixContains(query: String, paramSets: Seq[ParameterSetKeyData]): Seq[ParameterSetKeyData] =
    paramSets.filter(prefixContainsFilter(query))

  def prefixIsSubsystem(query: Subsystem, paramSets: Seq[ParameterSetKeyData]): Seq[ParameterSetKeyData] =
    paramSets.filter(prefixIsSubsystem(query))
}
