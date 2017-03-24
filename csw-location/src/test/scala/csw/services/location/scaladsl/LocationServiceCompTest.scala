package csw.services.location.scaladsl

import java.net.URI

import akka.actor.{Actor, ActorPath, Props}
import akka.serialization.Serialization
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.TestSink
import csw.services.location.common.TestFutureExtension.RichFuture
import csw.services.location.models.Connection.{AkkaConnection, HttpConnection, TcpConnection}
import csw.services.location.models._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}

class LocationServiceCompTest
  extends FunSuite
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  val actorRuntime = new ActorRuntime("test")
  val locationService: LocationService = LocationServiceFactory.make(actorRuntime)

  override protected def afterEach(): Unit = {
    locationService.unregisterAll().await
  }

  override protected def afterAll(): Unit = {
    actorRuntime.terminate().await
  }

  test("tcp location") {
    val Port = 1234
    val componentId = ComponentId("redis1", ComponentType.Service)
    val connection = TcpConnection(componentId)
    val location = new TcpLocation(connection,actorRuntime.hostname,Port)

    val result = locationService.register(location).await

    locationService.resolve(connection).await.get shouldBe location
    locationService.list.await shouldBe List(location)

    result.unregister().await

    locationService.resolve(connection).await shouldBe None
    locationService.list.await shouldBe List.empty
  }

  //#http_location_test
  test("http location") {
    val Port = 1234
    val componentId = ComponentId("configService", ComponentType.Service)
    val connection = HttpConnection(componentId)
    val Path = "path123"

    val resolvedHttpLocation = new HttpLocation(connection, actorRuntime.hostname, Port, Path)
    val registrationResult = locationService.register(resolvedHttpLocation).await
    registrationResult.componentId shouldBe componentId

    locationService.list.await shouldBe List(resolvedHttpLocation)

    registrationResult.unregister().await
    locationService.list.await shouldBe List.empty
  }
  //#http_location_test

  test("akka location") {
    val componentId = ComponentId("hcd1", ComponentType.HCD)
    val connection = AkkaConnection(componentId)
    val Prefix = "prefix"

    val actorRef = actorRuntime.actorSystem.actorOf(
      Props(new Actor {
        override def receive: Receive = Actor.emptyBehavior
      }),
      "my-actor-1"
    )
    val actorPath = ActorPath.fromString(Serialization.serializedActorPath(actorRef))

    val registrationResult = locationService.register(new AkkaLocation(connection, actorRef)).await

    registrationResult.componentId shouldBe componentId

    Thread.sleep(10)

    locationService.list.await shouldBe List(new AkkaLocation(connection, actorRef))

    registrationResult.unregister().await

    locationService.list.await shouldBe List.empty
  }

  test("tracking") {
    import actorRuntime._

    val Port = 1234
    val redis1Connection = TcpConnection(ComponentId("redis1", ComponentType.Service))
    val redis1Location = new TcpLocation(redis1Connection, actorRuntime.hostname, Port)

    val redis2Connection = TcpConnection(ComponentId("redis2", ComponentType.Service))
    val redis2Location = new TcpLocation(redis2Connection, actorRuntime.hostname, Port)

    val (switch, probe) = locationService.track(redis1Connection).toMat(TestSink.probe[TrackingEvent])(Keep.both).run()

    val result = locationService.register(redis1Location).await
    val result2 = locationService.register(redis2Location).await
    probe.request(1)
    probe.expectNext(LocationUpdated(redis1Location))

    result.unregister().await
    result2.unregister().await
    probe.request(1)
    probe.expectNext(LocationRemoved(redis1Connection))

    switch.shutdown()
    probe.request(1)
    probe.expectComplete()
  }

  test("Can not register against already registered name and can not unregister already unregistered connection"){
    val connection = TcpConnection(ComponentId("redis4", ComponentType.Service))

    val duplicateLocation = new TcpLocation(connection, actorRuntime.hostname, 1234)
    val location = new TcpLocation(connection, actorRuntime.hostname, 1111)

    val result = locationService.register(location).await

    val illegalStateException1 = intercept[IllegalStateException]{
      locationService.register(duplicateLocation).await
    }

//    illegalStateException1.getMessage shouldBe s"can not register against already registered connection=${duplicateLocation.connection.name}. Current value=${location}"

    result.unregister().await
    result.unregister().await
    ////////////// update test
  }

  test ("Resolve tcp connection") {
    val connection = TcpConnection(ComponentId("redis5", ComponentType.Service))
    locationService.register(new TcpLocation(connection, actorRuntime.hostname, 1234)).await

    val resolvedCon = locationService.resolve(connection).await.get

    resolvedCon.connection shouldBe connection
  }

  test("Should filter components with component type") {
    val hcdConnection = AkkaConnection(ComponentId("hcd1", ComponentType.HCD))
    val actorRef = actorRuntime.actorSystem.actorOf(
      Props(new Actor {
        override def receive: Receive = Actor.emptyBehavior
      }),
      "my-actor-2"
    )
    val actorPath = ActorPath.fromString(Serialization.serializedActorPath(actorRef))
    val akkaUri = new URI(actorPath.toString)

    locationService.register(new AkkaLocation(hcdConnection, actorRef)).await

    val redisConnection = TcpConnection(ComponentId("redis", ComponentType.Service))
    locationService.register(new TcpLocation(redisConnection, actorRuntime.hostname, 1234)).await

    val configServiceConnection = TcpConnection(ComponentId("configservice", ComponentType.Service))
    locationService.register(new TcpLocation(configServiceConnection, actorRuntime.hostname, 1234)).await

    val filteredHCDs = locationService.list(ComponentType.HCD).await

    filteredHCDs.map(_.connection) shouldBe List(hcdConnection)

    val filteredServices = locationService.list(ComponentType.Service).await

    filteredServices.map(_.connection).toSet shouldBe Set(redisConnection, configServiceConnection)
  }

  test("should filter connections based on Connection type") {
    val hcdAkkaConnection = AkkaConnection(ComponentId("hcd1", ComponentType.HCD))
    val actorRef = actorRuntime.actorSystem.actorOf(
      Props(new Actor {
        override def receive: Receive = Actor.emptyBehavior
      }),
      "my-actor-3"
    )

    val actorPath = ActorPath.fromString(Serialization.serializedActorPath(actorRef))
    val akkaUri = new URI(actorPath.toString)

    locationService.register(new AkkaLocation(hcdAkkaConnection, actorRef)).await

    val redisTcpConnection = TcpConnection(ComponentId("redis", ComponentType.Service))
    locationService.register(new TcpLocation(redisTcpConnection, actorRuntime.hostname, 1234)).await

    val configTcpConnection = TcpConnection(ComponentId("configservice", ComponentType.Service))
    locationService.register(new TcpLocation(configTcpConnection, actorRuntime.hostname, 1234)).await

    val assemblyHttpConnection = HttpConnection(ComponentId("assembly1", ComponentType.Assembly))
    val registrationResult = locationService.register(new HttpLocation(assemblyHttpConnection, actorRuntime.hostname, 1234, "path123")).await

    val tcpConnections = locationService.list(ConnectionType.TcpType).await
    tcpConnections.map(_.connection).toSet shouldBe Set(redisTcpConnection, configTcpConnection)

    val httpConnections = locationService.list(ConnectionType.HttpType).await
    httpConnections.map(_.connection).toSet shouldBe Set(assemblyHttpConnection)

    val akkaConnections = locationService.list(ConnectionType.AkkaType).await
    akkaConnections.map(_.connection).toSet shouldBe Set(hcdAkkaConnection)
  }

  test("should filter connections based on hostname") {
    val tcpConnection = TcpConnection(ComponentId("redis", ComponentType.Service))
    locationService.register(new TcpLocation(tcpConnection, actorRuntime.hostname, 1234)).await

    val httpConnection = HttpConnection(ComponentId("assembly1", ComponentType.Assembly))
    val registrationResult = locationService.register(new HttpLocation(httpConnection, actorRuntime.hostname, 1234, "path123")).await

    val filteredLocations = locationService.list(actorRuntime.ipaddr.getHostAddress).await

    filteredLocations.map(_.connection).toSet shouldBe Set(tcpConnection, httpConnection)

    locationService.list("Invalid_hostname").await shouldBe List.empty
  }
}
