package csw.common.framework.exceptions

case class FailureStop()       extends RuntimeException
case class FailureRestart()    extends RuntimeException
case class InitializeTimeOut() extends RuntimeException
case class ClusterSeedsNotFound()
    extends RuntimeException(
      "clusterSeeds setting is not specified either as env variable or system property. " +
      "Please check online documentation for this set-up."
    )