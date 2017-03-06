package csw.services.tracklocation

import java.net.URI
import java.nio.file.Paths

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import csw.services.location.common.{ActorRuntime, Networks}
import csw.services.location.models.Connection.TcpConnection
import csw.services.location.models._
import csw.services.location.scaladsl.LocationService
import csw.services.tracklocation.common.TestFutureExtension._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuiteLike, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Test the trackLocation app in-line
  */

class TrackLocationTest
  extends FunSuiteLike
    with Matchers
    with LazyLogging
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  private val actorRuntimePort = 2553

  override protected def afterEach(): Unit = {
  }

  implicit val timeout = Timeout(60.seconds)

  test("Test with command line args") {
    val name = "test1"
    val port = 9999

    val runtime = new ActorRuntime("track-location-test", Map("akka.remote.netty.tcp.port" -> actorRuntimePort))
    val system = runtime.actorSystem
    val locationService = LocationService.make(runtime)
    implicit val dispatcher = system.dispatcher

    Future {
      TrackLocation.main(
        Array(
          "--name",
          name,
          "--command",
          "sleep 10",
          "--port",
          port.toString,
          "--no-exit"
        ))
    }

    val connection = TcpConnection(ComponentId(name, ComponentType.Service))

    val uri = new URI(s"tcp://${Networks.getPrimaryIpv4Address.getHostAddress}:$port")

    val resolvedConnection: Resolved = locationService.resolve(connection).await

    resolvedConnection shouldBe ResolvedTcpLocation(connection, uri)

    locationService.unregisterAll().await
    runtime.actorSystem.terminate().await
    TrackLocation.shutdown().await
  }

  test("Test with config file") {
    val name = "test2"
    val port = 8888
    val url = getClass.getResource("/test2.conf")
    val configFile = Paths.get(url.toURI).toFile.getAbsolutePath

    val runtime = new ActorRuntime("track-location-test", Map("akka.remote.netty.tcp.port" -> actorRuntimePort))
    val system = runtime.actorSystem
    val locationService = LocationService.make(runtime)
    implicit val dispatcher = system.dispatcher

    Future {
      TrackLocation.main(Array("--name", name, "--no-exit", configFile))
    }

    val connection = TcpConnection(ComponentId(name, ComponentType.Service))
    val resolvedConnection: Resolved = locationService.resolve(connection).await
    val uri = new URI(s"tcp://${Networks.getPrimaryIpv4Address.getHostAddress}:$port")
    resolvedConnection shouldBe ResolvedTcpLocation(connection, uri)

    locationService.unregisterAll().await
    runtime.actorSystem.terminate().await
    TrackLocation.shutdown().await
  }
}
