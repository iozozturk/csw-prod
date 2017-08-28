package csw.common.framework.internal.supervisor

import akka.typed.scaladsl.TimerScheduler
import akka.typed.testkit.scaladsl.TestProbe
import akka.typed.testkit.{Inbox, StubbedActorContext}
import csw.common.components.ComponentDomainMessage
import csw.common.framework.ComponentInfos._
import csw.common.framework.FrameworkTestSuite
import csw.common.framework.models.InitialMessage.Run
import csw.common.framework.models.PreparingToShutdownMessage.{ShutdownComplete, ShutdownFailure, ShutdownTimeout}
import csw.common.framework.models.PubSub.{Publish, Subscribe, Unsubscribe}
import csw.common.framework.models.RunningMessage.{DomainMessage, Lifecycle}
import csw.common.framework.models.SupervisorCommonMessage.{
  ComponentStateSubscription,
  HaltComponent,
  LifecycleStateSubscription
}
import csw.common.framework.models.SupervisorIdleComponentMessage.{InitializeFailure, Initialized, Running}
import csw.common.framework.models.SupervisorIdleMessage.RegistrationComplete
import csw.common.framework.models.{ToComponentLifecycleMessage, _}
import csw.common.framework.scaladsl.ComponentHandlers
import csw.param.states.CurrentState
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar

// DEOPSCSW-163: Provide admin facilities in the framework through Supervisor role
class SupervisorBehaviorLifecycleTest extends FrameworkTestSuite with MockitoSugar with BeforeAndAfterEach {

  class TestData {
    val sampleHcdHandler: ComponentHandlers[ComponentDomainMessage] = mock[ComponentHandlers[ComponentDomainMessage]]
    val ctx                                                         = new StubbedActorContext[SupervisorMessage]("test-supervisor", 100, system)
    val timer: TimerScheduler[SupervisorMessage]                    = mock[TimerScheduler[SupervisorMessage]]
    val containerIdleMessageProbe: TestProbe[ContainerIdleMessage]  = TestProbe[ContainerIdleMessage]
    val supervisor =
      new SupervisorBehavior(
        ctx,
        Some(containerIdleMessageProbe.testActor),
        timer,
        hcdInfo,
        getSampleHcdWiring(sampleHcdHandler),
        registrationFactory,
        locationService
      )
    val childComponentInbox: Inbox[ComponentMessage]                    = ctx.childInbox(supervisor.component.upcast)
    val childPubSubLifecycleInbox: Inbox[PubSub[LifecycleStateChanged]] = ctx.childInbox(supervisor.pubSubLifecycle)
    val childPubSubCompStateInbox: Inbox[PubSub[CurrentState]]          = ctx.childInbox(supervisor.pubSubComponent)
  }

  test("supervisor should start in Idle mode and spawn three actors") {
    val testData = new TestData
    import testData._

    supervisor.mode shouldBe SupervisorMode.Idle
    ctx.children.size shouldBe 3
  }

  // *************** Begin testing of onIdleMessages ***************
  test("supervisor should accept Initialized message and send Run message to TLA") {
    val testData = new TestData
    import testData._

    val childRef = childComponentInbox.ref.upcast
    supervisor.onMessage(Initialized(childRef))

    verify(locationService).register(akkaRegistration)
    supervisor.onMessage(RegistrationComplete(registrationResult, childRef))

    childComponentInbox.receiveMsg() shouldBe Run
    supervisor.mode shouldBe SupervisorMode.Idle
  }

  test("supervisor should accept InitializeFailure message and change its mode") {
    val testData = new TestData
    import testData._

    supervisor.onMessage(InitializeFailure("test message for initialization failure"))
    supervisor.mode shouldBe SupervisorMode.InitializeFailure
  }

  test("supervisor should accept Running message from component and change its mode and publish state change") {
    val testData = new TestData
    import testData._

    supervisor.onMessage(Running(childComponentInbox.ref))

    supervisor.mode shouldBe SupervisorMode.Running
    childPubSubLifecycleInbox.receiveMsg() shouldBe Publish(LifecycleStateChanged(ctx.self, SupervisorMode.Running))
  }
  // *************** End of testing onIdleMessages ***************

  // *************** Begin testing of onCommonMessages ***************
  test("supervisor should handle LifecycleStateSubscription message by coordinating with pub sub actor") {
    val testData = new TestData
    import testData._

    val previousSupervisorMode = supervisor.mode
    val subscriberProbe        = TestProbe[LifecycleStateChanged]

    // Subscribe
    supervisor.onMessage(LifecycleStateSubscription(Subscribe(subscriberProbe.ref)))
    supervisor.mode shouldBe previousSupervisorMode
    val subscribeMessage = childPubSubLifecycleInbox.receiveMsg()
    subscribeMessage shouldBe Subscribe[LifecycleStateChanged](subscriberProbe.ref)

    // Unsubscribe
    supervisor.onMessage(LifecycleStateSubscription(Unsubscribe[LifecycleStateChanged](subscriberProbe.ref)))
    supervisor.mode shouldBe previousSupervisorMode
    val unsubscribeMessage = childPubSubLifecycleInbox.receiveMsg()
    unsubscribeMessage shouldBe Unsubscribe[LifecycleStateChanged](subscriberProbe.ref)
  }

  test("supervisor should handle ComponentStateSubscription message by coordinating with pub sub actor") {
    val testData = new TestData
    import testData._

    val subscriberProbe        = TestProbe[CurrentState]
    val previousSupervisorMode = testData.supervisor.mode

    // Subscribe
    supervisor.onMessage(ComponentStateSubscription(Subscribe[CurrentState](subscriberProbe.ref)))
    supervisor.mode shouldBe previousSupervisorMode
    val subscribeMessage = childPubSubCompStateInbox.receiveMsg()
    subscribeMessage shouldBe Subscribe[CurrentState](subscriberProbe.ref)

    // Unsubscribe
    supervisor.onMessage(ComponentStateSubscription(Unsubscribe[CurrentState](subscriberProbe.ref)))
    supervisor.mode shouldBe previousSupervisorMode
    val unsubscribeMessage = childPubSubCompStateInbox.receiveMsg()
    unsubscribeMessage shouldBe Unsubscribe[CurrentState](subscriberProbe.ref)
  }

  test("supervisor should handle HaltComponent message by shutting down all child actors in all the mode") {
    val testData = new TestData
    import testData._

    // put supervisor in InitializeFailure mode
    val initialMode = SupervisorMode.InitializeFailure
    supervisor.onMessage(InitializeFailure("Unexpected error"))
    supervisor.mode shouldBe initialMode

    // HaltComponent
    supervisor.onMessage(HaltComponent)
    supervisor.mode shouldBe SupervisorMode.PreparingToShutdown
    supervisor.haltingFlag shouldBe true

    // HaltComponent schedules Shutdown message to self
    supervisor.onMessage(ShutdownComplete)
    supervisor.haltingFlag shouldBe true
    supervisor.mode shouldBe SupervisorMode.Shutdown
  }
  // *************** End of testing onCommonMessages ***************

  /**
   * Below Tests show that all external messages for the TLA are received by the Supervisor
   * which passes them to TLA (depending on lifecycle)
  **/
  // *************** Begin testing of onRunning Messages ***************
  test("supervisor should handle lifecycle Shutdown message") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))
    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.Shutdown))
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.PreparingToShutdown))
    )
    supervisor.mode shouldBe SupervisorMode.PreparingToShutdown
    childComponentInbox.receiveAll() should contain(Lifecycle(ToComponentLifecycleMessage.Shutdown))
  }

  test("supervisor should handle lifecycle Restart message") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))
    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.Restart))
    supervisor.mode shouldBe SupervisorMode.Idle
    childComponentInbox.receiveAll() should contain(Lifecycle(ToComponentLifecycleMessage.Restart))
  }

  test("supervisor should handle lifecycle GoOffline message") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))
    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.GoOffline))
    supervisor.mode shouldBe SupervisorMode.RunningOffline
    childComponentInbox.receiveAll() should contain(Lifecycle(ToComponentLifecycleMessage.GoOffline))
  }

  test("supervisor should handle lifecycle GoOnline message") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))
    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.GoOffline))
    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.GoOnline))
    supervisor.mode shouldBe SupervisorMode.Running
    childComponentInbox.receiveAll() should contain(Lifecycle(ToComponentLifecycleMessage.GoOnline))
  }

  test("supervisor should accept and forward Domain message to a TLA") {
    val testData = new TestData
    import testData._

    sealed trait TestDomainMessage extends DomainMessage
    case object TestCompMessage$   extends TestDomainMessage

    supervisor.onMessage(Running(childComponentInbox.ref))
    supervisor.onMessage(TestCompMessage$)
    childComponentInbox.receiveMsg() shouldBe TestCompMessage$
  }
  // *************** End of testing onRunning Messages ***************

  // *************** Begin testing of onPreparingToShutdown Messages ***************
  test("supervisor should handle ShutdownTimeout message from TLA") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))

    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.Shutdown))

    verify(timer).startSingleTimer(SupervisorBehavior.TimerKey, ShutdownTimeout, SupervisorBehavior.shutdownTimeout)

    supervisor.mode shouldBe SupervisorMode.PreparingToShutdown
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.PreparingToShutdown))
    )

    supervisor.onMessage(ShutdownTimeout)
    verify(timer, never).cancel(SupervisorBehavior.TimerKey)

    supervisor.mode shouldBe SupervisorMode.ShutdownFailure
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.ShutdownFailure))
    )
  }

  test("supervisor should handle ShutdownFailure message from TLA") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))

    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.Shutdown))

    verify(timer).startSingleTimer(SupervisorBehavior.TimerKey, ShutdownTimeout, SupervisorBehavior.shutdownTimeout)

    supervisor.mode shouldBe SupervisorMode.PreparingToShutdown
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.PreparingToShutdown))
    )

    supervisor.onMessage(ShutdownFailure("Exception occurred"))
    verify(timer).cancel(SupervisorBehavior.TimerKey)

    supervisor.mode shouldBe SupervisorMode.ShutdownFailure
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.ShutdownFailure))
    )
  }

  test("supervisor should handle ShutdownComplete message from TLA") {
    val testData = new TestData
    import testData._

    supervisor.registrationOpt = Some(registrationResult)
    supervisor.onMessage(Running(childComponentInbox.ref))

    supervisor.onMessage(Lifecycle(ToComponentLifecycleMessage.Shutdown))

    verify(timer).startSingleTimer(SupervisorBehavior.TimerKey, ShutdownTimeout, SupervisorBehavior.shutdownTimeout)

    supervisor.mode shouldBe SupervisorMode.PreparingToShutdown
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.PreparingToShutdown))
    )

    supervisor.onMessage(ShutdownComplete)
    verify(timer).cancel(SupervisorBehavior.TimerKey)

    supervisor.mode shouldBe SupervisorMode.Shutdown
    childPubSubLifecycleInbox.receiveAll() should contain(
      Publish(LifecycleStateChanged(ctx.self, SupervisorMode.Shutdown))
    )
  }
  // *************** End of testing onPreparingToShutdown Messages ***************
}
