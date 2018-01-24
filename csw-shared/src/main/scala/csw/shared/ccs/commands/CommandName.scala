package csw.shared.ccs.commands

import play.api.libs.json.{Json, OFormat}

/**
 * Model representing the name as an identifier of a command
 */
case class CommandName(name: String)

object CommandName {
  implicit val format: OFormat[CommandName] = Json.format[CommandName]
}
