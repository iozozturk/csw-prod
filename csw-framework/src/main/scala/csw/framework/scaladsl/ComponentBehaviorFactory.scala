package csw.framework.scaladsl

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.framework.internal.component.ComponentBehavior
import csw.framework.models.ComponentInfo
import csw.param.messages.{ComponentMessage, FromComponentLifecycleMessage}
import csw.param.messages.PubSub.PublisherMessage
import csw.param.messages.RunningMessage.DomainMessage
import csw.param.states.CurrentState
import csw.services.location.scaladsl.LocationService

import scala.reflect.ClassTag

abstract class ComponentBehaviorFactory[Msg <: DomainMessage: ClassTag] {

  protected[framework] def handlers(
      ctx: ActorContext[ComponentMessage],
      componentInfo: ComponentInfo,
      pubSubRef: ActorRef[PublisherMessage[CurrentState]],
      locationService: LocationService
  ): ComponentHandlers[Msg]

  def make(
      compInfo: ComponentInfo,
      supervisor: ActorRef[FromComponentLifecycleMessage],
      pubSubRef: ActorRef[PublisherMessage[CurrentState]],
      locationService: LocationService
  ): Behavior[Nothing] =
    Actor
      .mutable[ComponentMessage](
        ctx ⇒
          new ComponentBehavior[Msg](
            ctx,
            compInfo,
            supervisor,
            handlers(ctx, compInfo, pubSubRef, locationService),
            locationService
        )
      )
      .narrow
}