package csw.services.event.perf.testkit

import java.io.File

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec

import scala.concurrent.Future
import scala.concurrent.duration._

trait PerfFlamesSupport { _: MultiNodeSpec ⇒

  /**
   * Runs `perf-java-flames` script on given node (JVM process).
   * Refer to https://github.com/jrudolph/perf-map-agent for options and manual.
   *
   * Options are currently to be passed in via `export PERF_MAP_OPTIONS` etc.
   */
  def runPerfFlames(nodes: RoleName*)(delay: FiniteDuration, time: FiniteDuration = 15.seconds): Unit = {
    if (isPerfJavaFlamesAvailable && isNode(nodes: _*)) {
      import scala.concurrent.ExecutionContext.Implicits.global

      val afterDelay = akka.pattern.after(delay, system.scheduler)(Future.successful("GO!"))
      afterDelay onComplete { it ⇒
        import java.lang.management._
        val name = ManagementFactory.getRuntimeMXBean.getName
        val pid  = name.substring(0, name.indexOf('@')).toInt

        val perfCommand = s"$perfJavaFlamesPath $pid"
        println(s"[perf @ $myself($pid)][OUT]: " + perfCommand)

        import scala.sys.process._
        perfCommand.run(new ProcessLogger {
          override def buffer[T](f: ⇒ T): T   = f
          override def out(s: ⇒ String): Unit = println(s"[perf @ $myself($pid)][OUT] " + s)
          override def err(s: ⇒ String): Unit = println(s"[perf @ $myself($pid)][ERR] " + s)
        })
      }
    }
  }

  def perfJavaFlamesPath: String =
    "/Users/pritamkadam/TMT/perf-map-agent/bin"

  def isPerfJavaFlamesAvailable: Boolean = {
    val isIt = new File(perfJavaFlamesPath).exists()
    if (!isIt) println(s"WARN: perf-java-flames not available under [$perfJavaFlamesPath]! Skipping perf profiling.")
    isIt
  }

}