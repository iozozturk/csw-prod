package csw.services.logging

import akka.actor._

import scala.collection.mutable
import scala.concurrent.Promise

/**
 * Global state info for logging. Use with care!
 */
private[logging] object LoggingState {

  // Queue of messages sent before logger is started
  private[logging] val msgs = new mutable.Queue[LogActorMessages]()

  @volatile var doTrace: Boolean = false
  @volatile var doDebug: Boolean = false
  @volatile var doInfo: Boolean  = true
  @volatile var doWarn: Boolean  = true
  @volatile var doError: Boolean = true

//  private[logging] var loggingSys: LoggingSystem = null

  private[logging] var maybeLogActor: Option[ActorRef] = None
  @volatile private[logging] var loggerStopping        = false

  private[logging] var doTime: Boolean                   = false
  private[logging] var timeActorOption: Option[ActorRef] = None

  // Use to sync akka logging actor shutdown
  private[logging] val akkaStopPromise = Promise[Unit]
}
