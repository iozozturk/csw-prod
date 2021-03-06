package csw.services.event.internal.commons

import csw.messages.location.Connection.TcpConnection
import csw.messages.location.{ComponentId, ComponentType}

private[csw] object EventServiceConnection {
  val value = TcpConnection(ComponentId("EventServer", ComponentType.Service))
}
