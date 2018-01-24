//package csw.shared.ccs.commands.matchers
//
//import akka.util.Timeout
//import csw.shared.params.states.CurrentState
//
///**
// * The base trait to build Matchers to match given state against a predicate
// */
//trait StateMatcher {
//
//  def prefix: String
//
//  /**
//   * A predicate to match the current state
//   * @param current current state to be matched as represented by [[csw.shared.params.states.CurrentState]]
//   * @return true if match is successful, false otherwise
//   */
//  def check(current: CurrentState): Boolean
//
//  /**
//   * The maximum duration for which the matching is executed if not completed either successfully or unsuccessfully
//   */
//  def timeout: Timeout
//}
