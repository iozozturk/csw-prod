package csw.common.framework.internal.supervisor

import akka.typed.ActorRef
import akka.typed.scaladsl.adapter.UntypedActorSystemOps
import csw.common.framework.models.{ComponentInfo, ContainerIdleMessage, SupervisorInfo}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationServiceFactory, RegistrationFactory}

class SupervisorInfoFactory {
  def make(containerRef: ActorRef[ContainerIdleMessage], componentInfo: ComponentInfo): SupervisorInfo = {
    val system              = ActorSystemFactory.remote(s"${componentInfo.name}-system")
    val locationService     = LocationServiceFactory.make()
    val registrationFactory = new RegistrationFactory
    val supervisorBehavior =
      SupervisorBehaviorFactory.behavior(Some(containerRef), componentInfo, locationService, registrationFactory)
    val supervisorRef = system.spawn(supervisorBehavior, componentInfo.name)
    SupervisorInfo(system, supervisorRef, componentInfo)
  }
}
