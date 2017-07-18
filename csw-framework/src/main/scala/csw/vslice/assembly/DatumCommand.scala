package csw.vslice.assembly

import akka.typed.scaladsl.Actor.MutableBehavior
import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.param.Parameters.Setup
import csw.vslice.framework.CommandMsgs.{CommandStart, SetStateResponseE, StopCurrentCommand}
import csw.vslice.assembly.TromboneStateActor.{TromboneState, TromboneStateMsg}
import csw.vslice.ccs.CommandStatus.{Completed, Error, NoLongerValid}
import csw.vslice.ccs.Validation.WrongInternalStateIssue
import csw.vslice.framework.CommandMsgs
import csw.vslice.framework.HcdComponentLifecycleMessage.Running
import csw.vslice.framework.RunningHcdMsg.Submit
import csw.vslice.hcd.models.TromboneHcdState

class DatumCommand(s: Setup,
                   tromboneHCD: Running,
                   startState: TromboneState,
                   stateActor: Option[ActorRef[TromboneStateMsg]],
                   ctx: ActorContext[CommandMsgs])
    extends MutableBehavior[CommandMsgs] {

  import TromboneCommandHandler._
  import TromboneStateActor._

  private val setStateResponseAdapter: ActorRef[StateWasSet] = ctx.spawnAdapter(SetStateResponseE)

  override def onMessage(msg: CommandMsgs): Behavior[CommandMsgs] = msg match {
    case CommandStart(replyTo) =>
      if (startState.cmd.head == cmdUninitialized) {
        replyTo ! NoLongerValid(
          WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow datum")
        )
      } else {
        stateActor.foreach(
          _ ! SetState(cmdItem(cmdBusy),
                       moveItem(moveIndexing),
                       startState.sodiumLayer,
                       startState.nss,
                       setStateResponseAdapter)
        )
        tromboneHCD.hcdRef ! Submit(Setup(s.info, TromboneHcdState.axisDatumCK))
        TromboneCommandHandler.executeMatch(ctx, idleMatcher, tromboneHCD.pubSubRef, Some(replyTo)) {
          case Completed =>
            stateActor.foreach(
              _ ! SetState(cmdReady, moveIndexed, sodiumLayer = false, nss = false, setStateResponseAdapter)
            )
          case Error(message) =>
            println(s"Data command match failed with error: $message")
        }
      }
      this
    case StopCurrentCommand =>
      tromboneHCD.hcdRef ! Submit(TromboneHcdState.cancelSC(s.info))
      this

    case SetStateResponseE(response: StateWasSet) => // ignore confirmation
      this
  }
}

object DatumCommand {
  def make(s: Setup,
           tromboneHCD: Running,
           startState: TromboneState,
           stateActor: Option[ActorRef[TromboneStateMsg]]): Behavior[CommandMsgs] =
    Actor.mutable(ctx ⇒ new DatumCommand(s, tromboneHCD, startState, stateActor, ctx))
}