package csw.services.config.server.http

import akka.Done
import akka.http.scaladsl.Http
import csw.services.config.api.commons.ActorRuntime
import csw.services.config.server.Settings
import csw.services.location.internal.Networks
import csw.services.location.models.Connection.HttpConnection
import csw.services.location.models._
import csw.services.location.scaladsl.LocationService

import scala.async.Async._
import scala.concurrent.Future

class HttpService(locationService: LocationService, configServiceRoute: ConfigServiceRoute, settings: Settings, actorRuntime: ActorRuntime) {

  import actorRuntime._

  lazy val lazyBinding: Future[Http.ServerBinding] = async {
    val binding = await(
      Http().bindAndHandle(
      handler = configServiceRoute.route,
      interface = new Networks("").hostname(),
      port = settings.`service-port`
    )
    )
    println(s"Server online at http://${binding.localAddress.getHostName}:${binding.localAddress.getPort}/")
    println("=========== Registering Config Service Server with Location Service ===============")
    await(registerWithLocationService())
    binding
  }

  private def registerWithLocationService(): Future[RegistrationResult] = {
    val registration = HttpRegistration(HttpConnection(ComponentId("ConfigServiceServer", ComponentType.Service)), settings.`service-port`, "")
    locationService.register(registration)
  }

  def shutdown(): Future[Done] = async {
    await(await(lazyBinding).unbind())
    await(actorRuntime.actorSystem.terminate())
    await(locationService.shutdown())
    Done
  }

}
