package csw.services.location.scaladsl

import akka.typed.ActorRef
import csw.messages.location.Connection.AkkaConnection
import csw.services.location.models.AkkaRegistration

class RegistrationFactory {
  def akkaTyped(akkaConnection: AkkaConnection, actorRef: ActorRef[_]): AkkaRegistration =
    AkkaRegistration(akkaConnection, actorRef)
}
