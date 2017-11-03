package csw.framework.internal.pubsub

import akka.typed.ActorRef
import akka.typed.scaladsl.{Actor, ActorContext}
import csw.messages.{PubSub, SupervisorMessage}

/**
 * Factory for creating [[akka.typed.scaladsl.Actor.MutableBehavior]] of a pub sub actor
 */
class PubSubBehaviorFactory() {
  def make[T](ctx: ActorContext[SupervisorMessage], actorName: String, componentName: String): ActorRef[PubSub[T]] =
    ctx.spawn(Actor.mutable[PubSub[T]](ctx ⇒ new PubSubBehavior(ctx, componentName)), actorName)
}
