package csw.trombone.assembly.actors

import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.common.ccs.CommandStatus.CommandResponse
import csw.common.ccs.Validation
import csw.common.ccs.Validation.{Valid, Validation}
import csw.common.framework.models.Component.AssemblyInfo
import csw.common.framework.models.FromComponentLifecycleMessage.ShutdownComplete
import csw.common.framework.models.ToComponentLifecycleMessage.{LifecycleFailureInfo, Restart, RunOffline, RunOnline}
import csw.common.framework.models._
import csw.common.framework.scaladsl.AssemblyActor
import csw.param.Parameters.{Observe, Setup}
import csw.trombone.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.trombone.assembly.DiagPublisherMessages.{DiagnosticState, OperationsState}
import csw.trombone.assembly.ParamValidation._
import csw.trombone.assembly.TromboneCommandHandlerMsgs.NotFollowingMsgs
import csw.trombone.assembly._

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TromboneAssembly {
  def make(assemblyInfo: AssemblyInfo, supervisor: ActorRef[AssemblyComponentLifecycleMessage]): Behavior[Nothing] =
    Actor.mutable[AssemblyMsg](ctx ⇒ new TromboneAssembly(ctx, assemblyInfo, supervisor)).narrow
}

class TromboneAssembly(ctx: ActorContext[AssemblyMsg],
                       info: AssemblyInfo,
                       supervisor: ActorRef[AssemblyComponentLifecycleMessage])
    extends AssemblyActor[DiagPublisherMessages](ctx, info, supervisor) {

  private var diagPublsher: ActorRef[DiagPublisherMessages] = _

  private var commandHandler: ActorRef[NotFollowingMsgs] = _

  implicit var ac: AssemblyContext = _

  def onRun(): Unit = ()

  def initialize(): Future[Unit] = async {
    val (calculationConfig, controlConfig) = await(getAssemblyConfigs)
    ac = AssemblyContext(info, calculationConfig, controlConfig)

    val eventPublisher = ctx.spawnAnonymous(TrombonePublisher.make(ac))

    commandHandler = ctx.spawnAnonymous(TromboneCommandHandler.make(ac, runningHcd, Some(eventPublisher)))

    diagPublsher = ctx.spawnAnonymous(DiagPublisher.make(ac, runningHcd, Some(eventPublisher)))
  }

  def onDomainMsg(mode: DiagPublisherMessages): Unit = mode match {
    case DiagnosticState => diagPublsher ! DiagnosticState
    case OperationsState => diagPublsher ! OperationsState
    case _               ⇒
  }

  def onLifecycle(message: ToComponentLifecycleMessage): Unit = message match {
    case RunOnline  =>
    case RunOffline => println("Received running offline")
    case Restart    => println("Received dorestart")
    case ToComponentLifecycleMessage.Shutdown =>
      println("Received doshutdown")
      runningHcd.foreach(
        _.hcdRef ! RunningHcdMsg
          .Lifecycle(ToComponentLifecycleMessage.Shutdown)
      )
      supervisor ! ShutdownComplete
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      println(s"TromboneAssembly received failed lifecycle state: $state for reason: $reason")
  }

  private def getAssemblyConfigs: Future[(TromboneCalculationConfig, TromboneControlConfig)] = ???

  def setup(s: Setup, commandOriginator: Option[ActorRef[CommandResponse]]): Validation = {
    val validation = validateOneSetup(s)
    if (validation == Valid) {
      commandHandler ! TromboneCommandHandlerMsgs.Submit(
        s,
        commandOriginator.getOrElse(ctx.spawnAnonymous(Behavior.empty))
      )
    }
    validation
  }

  def observe(o: Observe, replyTo: Option[ActorRef[CommandResponse]]): Validation = Validation.Valid
}
