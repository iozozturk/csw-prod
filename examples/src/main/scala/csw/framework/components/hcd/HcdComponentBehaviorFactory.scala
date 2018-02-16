package csw.framework.components.hcd

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers, CurrentStatePublisher}
import csw.messages.framework.ComponentInfo
import csw.messages.{CommandResponseManagerMessage, TopLevelActorMessage}
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory

//#component-factory
class HcdComponentBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(
      ctx: ActorContext[TopLevelActorMessage],
      componentInfo: ComponentInfo,
      commandResponseManager: ActorRef[CommandResponseManagerMessage],
      currentStatePublisher: CurrentStatePublisher,
      locationService: LocationService,
      loggerFactory: LoggerFactory
  ): ComponentHandlers =
    new HcdComponentHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory)
}
//#component-factory
