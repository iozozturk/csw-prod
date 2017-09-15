package csw.common.components

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.common.framework.models.PubSub.PublisherMessage
import csw.common.framework.models.{ComponentInfo, ComponentMessage}
import csw.param.states.CurrentState
import csw.services.location.scaladsl.LocationService

import scala.concurrent.Future

class ComponentHandlerToSimulateFailure(
    ctx: ActorContext[ComponentMessage],
    componentInfo: ComponentInfo,
    pubSubRef: ActorRef[PublisherMessage[CurrentState]],
    locationService: LocationService
) extends SampleComponentHandlers(ctx, componentInfo, pubSubRef, locationService) {

  override def onShutdown(): Future[Unit] = {
    throw new RuntimeException(ComponentHandlerToSimulateFailure.exceptionMsg)
  }
}

object ComponentHandlerToSimulateFailure {
  val exceptionMsg = "Shutdown failure occurred"
}