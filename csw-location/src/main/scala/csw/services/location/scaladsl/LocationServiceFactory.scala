package csw.services.location.scaladsl

import akka.actor.ActorSystem
import csw.services.location.commons.{ClusterSettings, CswCluster}
import csw.services.location.internal._

/**
 * The factory is used to create LocationService instance. With each creation, a new ActorSystem will be created and will
 * become part of csw-cluster. Currently, the LocationService instance is created in `csw-framework`.
 *
 * @note hence, it is recommended to create a single instance of LocationService and use it throughout the application
 */
object LocationServiceFactory {

  /**
   * Create a LocationService instance to manage registrations
   *
   * @throws csw.services.location.exceptions.CouldNotEnsureDataReplication
   * @throws csw.services.location.exceptions.CouldNotJoinCluster
   * @return an instance of `LocationService`
   */
  def make(): LocationService = withCluster(CswCluster.make())

  /**
   * Create an LocationService instance to manage registrations
   *
   * @param actorSystem the actorSystem used to feed in `CswCluster` and use it's config properties to join the cluster
   * @throws csw.services.location.exceptions.CouldNotEnsureDataReplication
   * @throws csw.services.location.exceptions.CouldNotJoinCluster
   * @return an instance of `LocationService`
   */
  def withSystem(actorSystem: ActorSystem): LocationService =
    withCluster(CswCluster.withSystem(actorSystem))

  /**
   * Create an LocationService instance to manage registrations
   *
   * @note it is highly recommended to use this method for testing purpose only
   * @param clusterSettings the custom clusterSettings used to join the cluster
   * @throws csw.services.location.exceptions.CouldNotEnsureDataReplication
   * @throws csw.services.location.exceptions.CouldNotJoinCluster
   * @return an instance of `LocationService`
   */
  def withSettings(clusterSettings: ClusterSettings): LocationService =
    withCluster(CswCluster.withSettings(clusterSettings))

  /**
   * Create a LocationService instance to manage registrations
   *
   * @note it is highly recommended to use it for testing purpose only
   * @param cswCluster the cswCluster instance used to join the cluster
   * @throws csw.services.location.exceptions.CouldNotEnsureDataReplication
   * @throws csw.services.location.exceptions.CouldNotJoinCluster
   * @return an instance of `LocationService`
   */
  def withCluster(cswCluster: CswCluster): LocationService = {
    val locationService: LocationService = new LocationServiceImpl(cswCluster)
    // starts a DeathwatchActor each time a LocationService is created
    DeathwatchActor.start(cswCluster, locationService)
    locationService
  }
}
