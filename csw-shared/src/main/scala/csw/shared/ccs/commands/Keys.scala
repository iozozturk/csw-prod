package csw.shared.ccs.commands

import csw.shared.params.generics.{Key, KeyType}

object Keys {
  val CancelKey: Key[String] = KeyType.StringKey.make("cancelKey")
}
