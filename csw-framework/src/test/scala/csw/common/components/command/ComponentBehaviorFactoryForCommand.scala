package csw.common.components.command

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages.framework.ComponentInfo
import csw.messages.models.PubSub
import csw.messages.params.states.CurrentState
import csw.messages.{CommandResponseManagerMessage, ComponentMessage}
import csw.services.location.scaladsl.LocationService

class ComponentBehaviorFactoryForCommand extends ComponentBehaviorFactory[ComponentDomainMessage] {
  override def handlers(
      ctx: ActorContext[ComponentMessage],
      componentInfo: ComponentInfo,
      commandResponseManager: ActorRef[CommandResponseManagerMessage],
      pubSubRef: ActorRef[PubSub.PublisherMessage[CurrentState]],
      locationService: LocationService
  ): ComponentHandlers[ComponentDomainMessage] =
    new ComponentHandlerForCommand(ctx, componentInfo, commandResponseManager, pubSubRef, locationService)
}