package csw.framework.integration

import akka.actor
import akka.stream.{ActorMaterializer, Materializer}
import akka.typed.ActorSystem
import akka.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.typed.testkit.TestKitSettings
import akka.typed.testkit.scaladsl.TestProbe
import com.typesafe.config.ConfigFactory
import csw.common.FrameworkAssertions._
import csw.common.components.SampleComponentState._
import csw.framework.internal.wiring.{Container, FrameworkWiring}
import csw.param.messages.PubSub.Subscribe
import csw.param.messages.SupervisorCommonMessage.ComponentStateSubscription
import csw.param.messages.{ContainerLifecycleState, SupervisorExternalMessage}
import csw.param.models.location.ComponentId
import csw.param.models.location.ComponentType.{Assembly, HCD}
import csw.param.models.location.Connection.AkkaConnection
import csw.param.states.CurrentState
import csw.services.location.commons.ClusterSettings
import csw.services.location.scaladsl.{LocationService, LocationServiceFactory}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

// DEOPSCSW-218: Discover component connection information using Akka protocol
// DEOPSCSW-220: Access and Monitor components for current values
// DEOPSCSW-221: Avoid sending commands to non-executing components
class TrackConnectionsIntegrationTest extends FunSuite with Matchers with BeforeAndAfterAll {

  implicit val seedActorSystem: actor.ActorSystem     = ClusterSettings().onPort(3552).system
  private val containerActorSystem: actor.ActorSystem = ClusterSettings().joinLocal(3552).system

  implicit val typedSystem: ActorSystem[_]      = seedActorSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(typedSystem)

  implicit val mat: Materializer               = ActorMaterializer()
  private val locationService: LocationService = LocationServiceFactory.withSystem(seedActorSystem)

  private val filterAssemblyConnection = AkkaConnection(ComponentId("Filter", Assembly))
  private val instrumentHcdConnection  = AkkaConnection(ComponentId("Instrument_Filter", HCD))
  private val disperserHcdConnection   = AkkaConnection(ComponentId("Disperser", HCD))

  override protected def afterAll(): Unit = Await.result(seedActorSystem.terminate(), 5.seconds)

  test("should track connections when locationServiceUsage is RegisterAndTrackServices") {

    val wiring = FrameworkWiring.make(containerActorSystem)
    // start a container and verify it moves to running lifecycle state
    val containerRef =
      Await.result(Container.spawn(ConfigFactory.load("container_tracking_connections.conf"), wiring), 5.seconds)

    val containerLifecycleStateProbe = TestProbe[ContainerLifecycleState]("container-lifecycle-state-probe")
    val assemblyProbe                = TestProbe[CurrentState]("assembly-state-probe")
    val filterProbe                  = TestProbe[CurrentState]("filter-state-probe")
    val disperserProbe               = TestProbe[CurrentState]("disperser-state-probe")

    // initially container is put in Idle lifecycle state and wait for all the components to move into Running lifecycle state
    // ********** Message: GetContainerLifecycleState **********
    assertThatContainerIsRunning(containerRef, containerLifecycleStateProbe, 5.seconds)

    // resolve all the components from container using location service
    val filterAssemblyLocation = Await.result(locationService.find(filterAssemblyConnection), 5.seconds)
    val instrumentHcdLocation  = Await.result(locationService.find(instrumentHcdConnection), 5.seconds)
    val disperserHcdLocation   = Await.result(locationService.find(disperserHcdConnection), 5.seconds)

    val assemblySupervisor  = filterAssemblyLocation.get.typedRef[SupervisorExternalMessage]
    val filterSupervisor    = instrumentHcdLocation.get.typedRef[SupervisorExternalMessage]
    val disperserSupervisor = disperserHcdLocation.get.typedRef[SupervisorExternalMessage]

    // Subscribe to component's current state
    assemblySupervisor ! ComponentStateSubscription(Subscribe(assemblyProbe.ref))
    filterSupervisor ! ComponentStateSubscription(Subscribe(filterProbe.ref))
    disperserSupervisor ! ComponentStateSubscription(Subscribe(disperserProbe.ref))

    Thread.sleep(2000)
    assemblyProbe.expectMsg(CurrentState(prefix, Set(choiceKey.set(locationUpdatedChoice))))
    assemblyProbe.expectMsg(CurrentState(prefix, Set(choiceKey.set(locationUpdatedChoice))))

    Await.result(locationService.unregister(disperserHcdConnection), 5.seconds)
    assemblyProbe.expectMsg(CurrentState(prefix, Set(choiceKey.set(locationRemovedChoice))))

    Await.result(containerActorSystem.terminate(), 5.seconds)
  }
}