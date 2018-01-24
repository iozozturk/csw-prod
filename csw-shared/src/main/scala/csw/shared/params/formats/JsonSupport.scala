package csw.shared.params.formats

import csw.shared.ccs.commands._
import csw.shared.ccs.events._
import csw.shared.params.generics.Parameter
import csw.shared.params.models.{ObsId, Prefix, RunId}
import csw.shared.params.states.StateVariable.StateVariable
import csw.shared.params.states.{CurrentState, DemandState}
import play.api.libs.json._

object JsonSupport extends JsonSupport with DerivedJsonFormats with WrappedArrayProtocol

/**
 * Supports conversion of commands, state variables and events to/from JSON
 */
trait JsonSupport { self: DerivedJsonFormats with WrappedArrayProtocol ⇒

  // JSON formats
  lazy val paramSetFormat: Format[Set[Parameter[_]]] = implicitly[Format[Set[Parameter[_]]]]
  lazy val runIdFormat: Format[RunId]                = implicitly[Format[RunId]]
  lazy val obsIdFormat: Format[Option[ObsId]]        = implicitly[Format[Option[ObsId]]]
  lazy val prefixFormat: Format[Prefix]              = implicitly[Format[Prefix]]
  lazy val commandTypeFormat: Format[CommandName]    = implicitly[Format[CommandName]]
  lazy val eventInfoFormat: Format[EventInfo]        = implicitly[Format[EventInfo]]

  // config and event type JSON tags
  private val setupType        = classOf[Setup].getSimpleName
  private val observeType      = classOf[Observe].getSimpleName
  private val waitType         = classOf[Wait].getSimpleName
  private val statusEventType  = classOf[StatusEvent].getSimpleName
  private val observeEventType = classOf[ObserveEvent].getSimpleName
  private val systemEventType  = classOf[SystemEvent].getSimpleName
  private val currentStateType = classOf[CurrentState].getSimpleName
  private val demandStateType  = classOf[DemandState].getSimpleName

  private def unexpectedJsValueError(x: JsValue) = throw new RuntimeException(s"Unexpected JsValue: $x")

  /**
   * Writes a SequenceParameterSet to JSON
   *
   * @param result any instance of SequenceCommand
   * @tparam A the type of the command (implied)
   * @return a JsValue object representing the SequenceCommand
   */
  def writeSequenceCommand[A <: SequenceCommand](result: A): JsValue = {
    JsObject(
      Seq(
        "type"        → JsString(result.typeName),
        "runId"       → runIdFormat.writes(result.runId),
        "source"      → prefixFormat.writes(result.source),
        "commandName" → commandTypeFormat.writes(result.commandName),
        "obsId"       → obsIdFormat.writes(result.maybeObsId),
        "paramSet"    → Json.toJson(result.paramSet)
      )
    )
  }

  /**
   * Reads a SequenceCommand back from JSON
   *
   * @param json the parsed JSON
   * @return an instance of the given SequenceCommand type, or an exception if the JSON is not valid for that type
   */
  def readSequenceCommand[A <: SequenceCommand](json: JsValue): A = {
    json match {
      case JsObject(fields) =>
        (fields("type"), fields("runId"), fields("source"), fields("commandName"), fields("obsId"), fields("paramSet")) match {
          case (JsString(typeName), runId, source, commandName, obsId, paramSet) =>
            typeName match {
              case `setupType` =>
                Setup(runId.as[RunId],
                      source.as[Prefix],
                      commandName.as[CommandName],
                      obsId.as[Option[ObsId]],
                      paramSet.as[Set[Parameter[_]]]).asInstanceOf[A]
              case `observeType` =>
                Observe(runId.as[RunId],
                        source.as[Prefix],
                        commandName.as[CommandName],
                        obsId.as[Option[ObsId]],
                        paramSet.as[Set[Parameter[_]]]).asInstanceOf[A]
              case `waitType` =>
                Wait(runId.as[RunId],
                     source.as[Prefix],
                     commandName.as[CommandName],
                     obsId.as[Option[ObsId]],
                     paramSet.as[Set[Parameter[_]]]).asInstanceOf[A]
              case _ => unexpectedJsValueError(json)
            }
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
  }

  /**
   * Writes a state variable to JSON
   *
   * @param stateVariable any instance of StateVariable
   * @tparam A the type of the StateVariable (implied)
   * @return a JsValue object representing the StateVariable
   */
  def writeStateVariable[A <: StateVariable](stateVariable: A): JsValue = {
    JsObject(
      Seq(
        "type"     -> JsString(stateVariable.typeName),
        "prefix"   -> prefixFormat.writes(stateVariable.prefix),
        "paramSet" -> Json.toJson(stateVariable.paramSet)
      )
    )
  }

  /**
   * Reads a StateVariable back from JSON
   *
   * @param json the parsed JSON
   * @return an instance of the given StateVariable, or an exception if the JSON is not valid for that type
   */
  def readStateVariable[A <: StateVariable](json: JsValue): A = {
    json match {
      case JsObject(fields) =>
        (fields("type"), fields("prefix"), fields("paramSet")) match {
          case (JsString(typeName), prefix, paramSet) =>
            val ck = prefix.as[Prefix]
            typeName match {
              case `currentStateType` => CurrentState(ck, paramSetFormat.reads(paramSet).get).asInstanceOf[A]
              case `demandStateType`  => DemandState(ck, paramSetFormat.reads(paramSet).get).asInstanceOf[A]
              case _                  => unexpectedJsValueError(json)
            }
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
  }

  /**
   * Writes an event to JSON
   *
   * @param event any instance of EventType
   * @tparam A the type of the event (implied)
   * @return a JsValue object representing the event
   */
  def writeEvent[A <: EventType[_]](event: A): JsValue = {
    JsObject(
      Seq(
        "type"     -> JsString(event.typeName),
        "info"     -> eventInfoFormat.writes(event.info),
        "paramSet" -> Json.toJson(event.paramSet)
      )
    )
  }

  /**
   * Reads an event back from JSON
   *
   * @param json the parsed JSON
   * @tparam A the type of the event (use Any and match on the type if you don't know)
   * @return an instance of the given event type, or an exception if the JSON is not valid for that type
   */
  def readEvent[A <: EventType[_]](json: JsValue): A = {
    json match {
      case JsObject(fields) =>
        (fields("type"), fields("info"), fields("paramSet")) match {
          case (JsString(typeName), eventInfo, paramSet) =>
            val info = eventInfo.as[EventInfo]
            typeName match {
              case `statusEventType`  => StatusEvent(info, paramSetFormat.reads(paramSet).get).asInstanceOf[A]
              case `observeEventType` => ObserveEvent(info, paramSetFormat.reads(paramSet).get).asInstanceOf[A]
              case `systemEventType`  => SystemEvent(info, paramSetFormat.reads(paramSet).get).asInstanceOf[A]
              case _                  => unexpectedJsValueError(json)
            }
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
  }

  /**
   * Writes a Result to JSON
   *
   * @param result any instance of Result
   * @return a JsValue object representing the Result
   */
  def writeResult(result: Result): JsValue = {
    JsObject(
      Seq(
        "prefix"   -> prefixFormat.writes(result.prefix),
        "paramSet" -> Json.toJson(result.paramSet)
      )
    )
  }

  /**
   * Reads a Result back from JSON
   *
   * @param json the parsed JSON
   * @return an instance of Result, or an exception if the JSON is not valid for that type
   */
  def readResult(json: JsValue): Result = {
    json match {
      case JsObject(fields) =>
        (fields("prefix"), fields("paramSet")) match {
          case (prefix, paramSet) =>
            Result(prefix.as[Prefix], paramSetFormat.reads(paramSet).get)
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
  }
}
