package csw.services.event.perf

import java.util.concurrent.Executors

import akka.actor._
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.testkit._
import com.typesafe.config.ConfigFactory
import csw.services.event.perf.Messages.Init
import csw.services.logging.appenders.StdOutAppender
import csw.services.logging.scaladsl.LoggingSystemFactory

import scala.concurrent.duration._

object EventThroughputSpec extends MultiNodeConfig {
  val first  = role("first")
  val second = role("second")

  val barrierTimeout = 5.minutes

  val cfg = ConfigFactory.parseString(s"""
     include "logging.conf"

     # for serious measurements you should increase the totalMessagesFactor (20)
     csw.test.EventMaxThroughputSpec.totalMessagesFactor = 1.0
     csw.test.EventMaxThroughputSpec.actor-selection = off
     akka {
       loglevel = INFO
       log-dead-letters = 100
       # avoid TestEventListener
       testconductor.barrier-timeout = ${barrierTimeout.toSeconds}s
       actor {
         provider = remote
         serialize-creators = false
         serialize-messages = false
         allow-java-serialization = on

         serializers {
           kryo = "com.twitter.chill.akka.AkkaSerializer"
         }
         serialization-bindings {
           "csw.messages.TMTSerializable" = kryo
         }
       }
     }
     akka.remote.default-remote-dispatcher {
       fork-join-executor {
         # parallelism-factor = 0.5
         parallelism-min = 4
         parallelism-max = 4
       }
       # Set to 10 by default. Might be worthwhile to experiment with.
       # throughput = 100
     }

     """)

  commonConfig(debugConfig(on = false).withFallback(cfg).withFallback(RemotingMultiNodeSpec.commonConfig))

  sealed trait Target {
    def tell(msg: Any, sender: ActorRef): Unit
    def ref: ActorRef
  }

  final case class ActorRefTarget(override val ref: ActorRef) extends Target {
    override def tell(msg: Any, sender: ActorRef) = ref.tell(msg, sender)
  }

  final case class ActorSelectionTarget(sel: ActorSelection, override val ref: ActorRef) extends Target {
    override def tell(msg: Any, sender: ActorRef) = sel.tell(msg, sender)
  }
}

class EventThroughputSpecMultiJvmNode1 extends EventThroughputSpec
class EventThroughputSpecMultiJvmNode2 extends EventThroughputSpec

abstract class EventThroughputSpec extends RemotingMultiNodeSpec(EventThroughputSpec) {

  import EventThroughputSpec._

  val totalMessagesFactor = system.settings.config.getDouble("csw.test.EventMaxThroughputSpec.totalMessagesFactor")
  val actorSelection      = system.settings.config.getBoolean("csw.test.EventMaxThroughputSpec.actor-selection")

  var plot = PlotResult()

  LoggingSystemFactory.start("", "", "", system).setAppenders(List(StdOutAppender))

  def adjustedTotalMessages(n: Long): Long = (n * totalMessagesFactor).toLong

  override def initialParticipants = roles.size

  lazy val reporterExecutor = Executors.newFixedThreadPool(1)
  def reporter(name: String): TestRateReporter = {
    val r = new TestRateReporter(name)
    reporterExecutor.execute(r)
    r
  }

  override def afterAll(): Unit = {
    reporterExecutor.shutdown()
    runOn(first) {
      println(plot.csv(system.name))
    }
    super.afterAll()
  }

  def identifyReceiver(name: String, r: RoleName = second): Target = {
    val sel = system.actorSelection(node(r) / "user" / name)
    sel ! Identify(None)
    val ref = expectMsgType[ActorIdentity](10.seconds).ref.get
    if (actorSelection) ActorSelectionTarget(sel, ref)
    else ActorRefTarget(ref)
  }

  val scenarios = List(
    TestSettings(testName = "warmup",
                 totalMessages = adjustedTotalMessages(10000),
                 burstSize = 1000,
                 payloadSize = 100,
                 senderReceiverPairs = 1),
    TestSettings(testName = "1-to-1",
                 totalMessages = adjustedTotalMessages(50000),
                 burstSize = 1000,
                 payloadSize = 100,
                 senderReceiverPairs = 1),
    TestSettings(testName = "1-to-1-size-1k",
                 totalMessages = adjustedTotalMessages(50000),
                 burstSize = 1000,
                 payloadSize = 1000,
                 senderReceiverPairs = 1),
    TestSettings(testName = "1-to-1-size-10k",
                 totalMessages = adjustedTotalMessages(20000),
                 burstSize = 1000,
                 payloadSize = 10000,
                 senderReceiverPairs = 1),
    TestSettings(testName = "5-to-5",
                 totalMessages = adjustedTotalMessages(20000),
                 burstSize = 200,
                 payloadSize = 100,
                 senderReceiverPairs = 5),
    TestSettings(testName = "10-to-10",
                 totalMessages = adjustedTotalMessages(10000),
                 burstSize = 1000,
                 payloadSize = 100,
                 senderReceiverPairs = 10)
  )

  def test(testSettings: TestSettings, resultReporter: BenchmarkFileReporter): Unit = {
    import testSettings._
    val receiverName = testName + "-subscriber"

//    runPerfFlames(first, second)(delay = 5.seconds, time = 15.seconds)

    runOn(second) {
      val rep = reporter(testName)
      val receivers = (1 to senderReceiverPairs).map { n ⇒
        system.actorOf(
          SubscribingActor.props(rep, payloadSize, printTaskRunnerMetrics = n == 1, senderReceiverPairs),
          receiverName + n
        )
      }
      enterBarrier(receiverName + "-started")
      enterBarrier(testName + "-done")
      receivers.foreach(_ ! PoisonPill)
      rep.halt()
    }

    runOn(first) {
      enterBarrier(receiverName + "-started")
      val ignore    = TestProbe()
      val receivers = (for (n ← 1 to senderReceiverPairs) yield identifyReceiver(receiverName + n)).toArray
      val senders = for (n ← 1 to senderReceiverPairs) yield {
        val receiver = receivers(n - 1)

        val plotProbe = TestProbe()
        val snd = system.actorOf(
          PublishingActor.props(
            receiver,
            receivers,
            testSettings,
            plotProbe.ref,
            printTaskRunnerMetrics = n == 1,
            resultReporter
          ),
          testName + "-publisher" + n
        )
        val terminationProbe = TestProbe()
        terminationProbe.watch(snd)
        snd ! Init
        (snd, terminationProbe, plotProbe)
      }
      senders.foreach {
        case (snd, terminationProbe, plotProbe) ⇒
          terminationProbe.expectTerminated(snd, barrierTimeout)
          if (snd == senders.head._1) {
            val plotResult = plotProbe.expectMsgType[PlotResult]
            plot = plot.addAll(plotResult)
          }
      }
      enterBarrier(testName + "-done")
    }

    enterBarrier("after-" + testName)
  }

  "Max throughput of Event Service" must {
    val reporter = BenchmarkFileReporter("MaxThroughputSpec", system)
    for (s ← scenarios) {
      s"be great for ${s.testName}, burstSize = ${s.burstSize}, payloadSize = ${s.payloadSize}" in test(s, reporter)
    }
  }
}
