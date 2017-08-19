package csw.common.framework.scaladsl

import akka.typed.Behavior
import akka.typed.scaladsl.Actor
import csw.common.framework.internal.Container
import csw.common.framework.models.{ComponentInfo, ContainerMsg}

object ContainerBehaviorFactory {
  def behavior(containerInfo: ComponentInfo): Behavior[ContainerMsg] =
    Actor.mutable(ctx ⇒ new Container(ctx, containerInfo))
}