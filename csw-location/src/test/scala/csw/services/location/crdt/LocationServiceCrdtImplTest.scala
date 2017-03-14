package csw.services.location.crdt

import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.TestSink
import csw.services.location.common.TestFutureExtension.RichFuture
import csw.services.location.scaladsl.ActorRuntime
import csw.services.location.scaladsl.models.Connection.TcpConnection
import csw.services.location.scaladsl.models.{ComponentId, ComponentType}
import org.scalatest.{FunSuite, Matchers}

class LocationServiceCrdtImplTest extends FunSuite with Matchers {

  test("register-unregister") {
    val actorRuntime = new ActorRuntime("test")
    val crdtImpl = new LocationServiceCrdtImpl(actorRuntime)

    val Port = 1234
    val componentId = ComponentId("redis1", ComponentType.Service)
    val connection = TcpConnection(componentId)
    val location = TcpServiceLocation(connection, Port)

    val result = crdtImpl.register(location).await

    crdtImpl.resolve(connection).await shouldBe location
    crdtImpl.list.await shouldBe List(location)

    result.unregister().await

    intercept[RuntimeException] {
      crdtImpl.resolve(connection).await
    }
    crdtImpl.list.await shouldBe List.empty
    actorRuntime.terminate().await
  }

  test("tracking") {
    val actorRuntime = new ActorRuntime("test")
    import actorRuntime._

    val crdtImpl = new LocationServiceCrdtImpl(actorRuntime)

    val Port = 1234
    val componentId = ComponentId("redis1", ComponentType.Service)
    val connection = TcpConnection(componentId)
    val location = TcpServiceLocation(connection, Port)

    val (switch, probe) = crdtImpl.track(connection).toMat(TestSink.probe[TrackingEvent])(Keep.both).run()

    val result = crdtImpl.register(location).await
    probe.request(1)
    probe.expectNext(Updated(location))

    result.unregister().await
    probe.request(1)
    probe.expectNext(Deleted(connection))

    switch.shutdown()
    probe.request(1)
    probe.expectComplete()

    actorRuntime.terminate().await
  }
}
