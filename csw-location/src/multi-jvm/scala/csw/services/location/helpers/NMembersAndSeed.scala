package csw.services.location.helpers

import akka.actor.ActorSystem
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.{Config, ConfigFactory}
import csw.services.location.commons.ClusterSettings

class NMembersAndSeed(n: Int) extends MultiNodeConfig {

  private val settings = ClusterSettings()

  commonConfig(debugConfig(on = false)
    .withFallback(
    ConfigFactory.parseString("""
      akka.loglevel = ERROR
      akka.remote.netty.tcp.applied-adapters = []
      akka.remote.log-remote-lifecycle-events = ERROR
    """)))

  testTransport(on = true)

  def makeSystem(config: Config): ActorSystem = ActorSystem(settings.clusterName, config)

  val seed: RoleName = addRole("seed")(settings.onPort(3552))

  val members: Vector[RoleName] = (1 to n).toVector.map { x =>
    addRole(s"member-$x")(settings.joinLocal(3552))
  }

  private def addRole(name: String)(settings: ClusterSettings): RoleName = {
    val node = role(name)
    nodeConfig(node)(settings.config)
    node
  }
}

class OneMemberAndSeed extends NMembersAndSeed(1) {
  val Vector(member) = members
}

class TwoMembersAndSeed extends NMembersAndSeed(2) {
  val Vector(member1, member2) = members
}
