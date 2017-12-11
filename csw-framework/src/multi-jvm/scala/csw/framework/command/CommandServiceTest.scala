package csw.framework.command

import akka.actor.Scheduler
import akka.stream.{ActorMaterializer, Materializer}
import akka.typed.ActorSystem
import akka.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.typed.testkit.TestKitSettings
import akka.typed.testkit.scaladsl.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.common.utils.LockCommandFactory
import csw.framework.internal.wiring.{Container, FrameworkWiring, Standalone}
import csw.messages.CommandMessage.Submit
import csw.messages.ccs.CommandIssue.ComponentLockedIssue
import csw.messages.ccs.commands.CommandResponse._
import csw.messages.ccs.commands.{CommandResponse, Observe, Setup}
import csw.messages.location.Connection.AkkaConnection
import csw.messages.location.{ComponentId, ComponentType}
import csw.messages.models.LockingResponse
import csw.messages.models.LockingResponse.LockAcquired
import csw.messages.params.generics.{KeyType, Parameter}
import csw.messages.params.models.ObsId
import csw.messages.params.states.DemandState
import csw.services.ccs.common.ActorRefExts.RichComponentActor
import csw.services.ccs.internal.matchers.MatcherResponses.{MatchCompleted, MatchFailed}
import csw.services.ccs.internal.matchers.{DemandMatcher, Matcher, MatcherResponse}
import csw.services.location.helpers.{LSNodeSpec, TwoMembersAndSeed}

import scala.async.Async._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Test Configuration :
 * JVM-1 : Seed node
 * JVM-2 : Assembly running in Standalone mode (Commanding Assembly)
 * JVM-3 : Assembly and HCD running in Container Mode
 *
 * Scenario 1 : Short Running Command
 * 1. Assembly running in JVM-2 (Commanding Assembly) resolves Assembly running in JVM-3
 * 2. Commanding Assembly sends short running command to another assembly (JVM-3)
 * 3. Assembly (JVM-3) receives command and update its status as Invalid in CSRM
 * 4. Commanding Assembly (JVM-2) receives Command Completion response which is Invalid
 *
 * Scenario 2 : Long Running Command without matcher
 * 1. Commanding Assembly sends long running command to another assembly (JVM-3)
 * 2. Assembly (JVM-3) receives command and update its validation status as Accepted in CSRM
 * 3. Commanding Assembly (JVM-2) receives validation response as Accepted
 * 4. Commanding Assembly then waits for Command Completion response
 * 5. Assembly from JVM-3 updates Command Completion status which is CompletedWithResult in CSRM
 * 6. Commanding Assembly (JVM-2) receives Command Completion response which is CompletedWithResult
 *
 * Scenario 3 : Long Running Command with matcher
 * 1. Commanding Assembly sends long running command to another assembly (JVM-3)
 * 2. Assembly (JVM-3) receives command and update its validation status as Accepted in CSRM
 * 3. Commanding Assembly (JVM-2) receives validation response as Accepted
 * 4. Commanding Assembly starts state matcher
 * 5. Assembly (JVM-3) keeps publishing its current state
 * 6. Commanding Assembly marks status of Command as Completed when demand state matches with current state=
**/
class CommandServiceTestMultiJvm1 extends CommandServiceTest(0)
class CommandServiceTestMultiJvm2 extends CommandServiceTest(0)
class CommandServiceTestMultiJvm3 extends CommandServiceTest(0)

// DEOPSCSW-201: Destination component to receive a submit command
// DEOPSCSW-202: Verification of submit commands
// DEOPSCSW-207: Report on Configuration Command Completion
// DEOPSCSW-208: Report failure on Configuration Completion command
// DEOPSCSW-217: Execute RPC like commands
// DEOPSCSW-222: Locking a component for a specific duration
// DEOPSCSW-224: Inter component command sending
// DEOPSCSW-225: Allow components to receive commands
// DEOPSCSW-228: Assist Components with command completion
// DEOPSCSW-313: Support short running actions by providing immediate response
class CommandServiceTest(ignore: Int) extends LSNodeSpec(config = new TwoMembersAndSeed) {

  import config._
  import csw.common.components.command.ComponentStateForCommand._

  implicit val actorSystem: ActorSystem[_] = system.toTyped
  implicit val mat: Materializer           = ActorMaterializer()
  implicit val ec: ExecutionContext        = actorSystem.executionContext
  implicit val timeout: Timeout            = 5.seconds
  implicit val scheduler: Scheduler        = actorSystem.scheduler
  implicit val testkit: TestKitSettings    = TestKitSettings(actorSystem)
  test("sender of command should receive appropriate responses") {

    runOn(seed) {
      // cluster seed is running on jvm-1
      enterBarrier("spawned")

      // resolve assembly running in jvm-3 and send setup command expecting immediate command completion response
      val assemblyLocF = locationService.resolve(AkkaConnection(ComponentId("Assembly", ComponentType.Assembly)), 5.seconds)
      val assemblyRef  = Await.result(assemblyLocF, 10.seconds).map(_.componentRef()).get

      enterBarrier("short-long-commands")
      enterBarrier("assembly-locked")

      val cmdResponseProbe = TestProbe[CommandResponse]

      // try to send a command to assembly which is already locked
      val assemblyObserve = Observe(prefix, acceptedCmdPrefix, Some(ObsId("Obs001")))
      assemblyRef ! Submit(assemblyObserve, cmdResponseProbe.ref)
      val response = cmdResponseProbe.expectMsgType[NotAllowed]
      response.issue shouldBe an[ComponentLockedIssue]

      enterBarrier("command-when-locked")
    }

    runOn(member1) {
      val cmdResponseProbe = TestProbe[CommandResponse]
      val obsId            = Some(ObsId("Obs001"))

      // spawn single assembly running in Standalone mode in jvm-2
      val wiring        = FrameworkWiring.make(system, locationService)
      val sequencerConf = ConfigFactory.load("command/commanding_assembly.conf")
      Await.result(Standalone.spawn(sequencerConf, wiring), 5.seconds)
      enterBarrier("spawned")

      // resolve assembly running in jvm-3 and send setup command expecting immediate command completion response
      val assemblyLocF = locationService.resolve(AkkaConnection(ComponentId("Assembly", ComponentType.Assembly)), 5.seconds)
      val assemblyRef  = Await.result(assemblyLocF, 10.seconds).map(_.componentRef()).get

      // short running command
      val shortCommandResponse = Await.result(assemblyRef.submit(Setup(prefix, invalidCmdPrefix, obsId)), timeout.duration)
      shortCommandResponse shouldBe a[Invalid]

      // long running command which does not use matcher
      val setupWithoutMatcher = Setup(prefix, withoutMatcherPrefix, obsId)

      val eventualLongCommandResponse = async {
        val initialCommandResponse = await(assemblyRef.submit(setupWithoutMatcher))
        initialCommandResponse match {
          case _: Accepted ⇒ await(assemblyRef.getCommandResponse(setupWithoutMatcher.runId))
          case x           ⇒ x
        }
      }

      val longCommandResponse = Await.result(eventualLongCommandResponse, timeout.duration)

      longCommandResponse shouldBe a[CompletedWithResult]
      longCommandResponse.runId shouldBe setupWithoutMatcher.runId

      // DEOPSCSW-229: Provide matchers infrastructure for comparison
      // long running command which uses matcher
      val param: Parameter[Int] = KeyType.IntKey.make("encoder").set(100)
      val demandMatcher         = DemandMatcher(DemandState(matcherPrefix, Set(param)), withUnits = false, timeout)
      val setupWithMatcher      = Setup(prefix, matcherPrefix, obsId)
      val matcher               = new Matcher(assemblyRef, demandMatcher)

      val matcherResponseF: Future[MatcherResponse] = matcher.start

      val eventualCommandResponse: Future[CommandResponse] = async {
        val initialResponse = await(assemblyRef.oneway(setupWithMatcher))
        initialResponse match {
          case _: Accepted ⇒
            val matcherResponse = await(matcherResponseF)
            matcherResponse match {
              case MatchCompleted  ⇒ Completed(setupWithMatcher.runId)
              case MatchFailed(ex) ⇒ Error(setupWithMatcher.runId, ex.getMessage)
            }
          case invalid: Invalid ⇒
            matcher.stop()
            invalid
          case x ⇒ x
        }
      }

      val commandResponse = Await.result(eventualCommandResponse, timeout.duration)

      commandResponse shouldBe Completed(setupWithMatcher.runId)
      enterBarrier("short-long-commands")

      // acquire lock on assembly
      val lockResponseProbe = TestProbe[LockingResponse]
      assemblyRef ! LockCommandFactory.make(immediateCmdPrefix, lockResponseProbe.ref)
      lockResponseProbe.expectMsg(LockAcquired)
      enterBarrier("assembly-locked")

      // send command with lock token and expect command processing response
      val assemblySetup = Setup(prefix, immediateCmdPrefix, obsId)
      assemblyRef ! Submit(assemblySetup, cmdResponseProbe.ref)
      cmdResponseProbe.expectMsg(5.seconds, Completed(assemblySetup.runId))
      enterBarrier("command-when-locked")
    }

    runOn(member2) {
      // spawn container having assembly and hcd running in jvm-3
      val wiring        = FrameworkWiring.make(system, locationService)
      val containerConf = ConfigFactory.load("command/container.conf")
      Await.result(Container.spawn(containerConf, wiring), 5.seconds)
      enterBarrier("spawned")
      enterBarrier("short-long-commands")
      enterBarrier("assembly-locked")
      enterBarrier("command-when-locked")
    }

    enterBarrier("end")
  }
}