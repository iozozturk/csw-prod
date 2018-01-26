val enableCoverage         = sys.props.get("enableCoverage").contains("true")
val MaybeCoverage: Plugins = if (enableCoverage) Coverage else Plugins.empty

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `csw-logging`,
  `csw-logging-macros`,
  `csw-cluster-seed`,
  `csw-config-api`,
  `csw-config-client`,
  `csw-config-client-cli`,
  `csw-config-server`,
  `csw-framework`,
  `csw-command`,
  `csw-location`,
  `csw-location-agent`,
  `csw-benchmark`,
  `csw-vslice`,
  `csw-messages`,
  `csw-commons`,
  `integration`,
  `examples`
)

lazy val unidocExclusions: Seq[ProjectReference] = Seq(
  `csw-logging-macros`,
  `csw-cluster-seed`,
  `csw-location-agent`,
  `csw-config-server`,
  `csw-config-client-cli`,
  `csw-benchmark`,
  `csw-vslice`,
  `integration`,
  `examples`
)

//Root project
lazy val `csw-prod` = project
  .in(file("."))
  .settings(Common.commonSettings)
  .enablePlugins(UnidocSite, PublishGithub, GitBranchPrompt, ScalaJSPlugin)
  .aggregate(aggregatedProjects: _*)
  .settings(Settings.mergeSiteWith(docs))
  .settings(Settings.docExclusions(unidocExclusions))


// contains simple case classes used for data transfer that are shared between the client and server
lazy val `csw-shared` = (crossProject.crossType(CrossType.Pure) in file("csw-shared"))
  .settings(Common.commonSettings)
  .settings(
    libraryDependencies ++= Dependencies.Shared.value,
    Common.detectCycles := false,
    PB.targets in Compile := Seq(
      PB.gens.java                        -> (sourceManaged in Compile).value,
      scalapb.gen(javaConversions = false) -> (sourceManaged in Compile).value
    )
  )


lazy val `csw-shared-Jvm` = `csw-shared`.jvm

lazy val `csw-shared-Js` = `csw-shared`.js

lazy val `csw-messages` = project
  .settings(Common.projectSettings)
  .enablePlugins(PublishBintray, GenJavadocPlugin)
  .dependsOn(`csw-shared-Jvm`)
  .settings(
    libraryDependencies ++= Dependencies.Messages
  )
  .settings(
    Common.detectCycles := false,
    PB.targets in Compile := Seq(
      PB.gens.java                        -> (sourceManaged in Compile).value,
      scalapb.gen(javaConversions = true) -> (sourceManaged in Compile).value
    )
  )

lazy val `csw-logging-macros` = project
  .settings(Common.projectSettings)
  .settings(
    libraryDependencies += Libs.`scala-reflect`
  )

//Logging service
lazy val `csw-logging` = project
  .settings(Common.projectSettings)
  .dependsOn(`csw-logging-macros`, `csw-messages`)
  .enablePlugins(PublishBintray, GenJavadocPlugin, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.Logging
  )

//Location service related projects
lazy val `csw-location` = project
  .settings(Common.projectSettings)
  .dependsOn(`csw-logging`, `csw-messages`)
  .enablePlugins(PublishBintray, GenJavadocPlugin, AutoMultiJvm, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.Location
  )

//Cluster seed
lazy val `csw-cluster-seed` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-messages`,
    `csw-location`,
    `csw-commons`,
    `csw-framework`     % "test->test",
    `csw-config-server` % "test->test",
  )
  .enablePlugins(DeployApp, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.CswClusterSeed
  )

lazy val `csw-location-agent` = project
  .settings(Common.projectSettings)
  .dependsOn(`csw-location`)
  .enablePlugins(DeployApp, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.CswLocationAgent
  )

//Config service related projects
lazy val `csw-config-api` = project
  .settings(Common.projectSettings)
  .enablePlugins(GenJavadocPlugin, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.ConfigApi
  )

lazy val `csw-config-server` = project
  .settings(Common.projectSettings)
  .dependsOn(`csw-location`, `csw-config-api`, `csw-commons`)
  .enablePlugins(DeployApp, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.ConfigServer
  )

lazy val `csw-config-client` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-config-api`,
    `csw-commons`,
    `csw-config-server` % "test->test",
    `csw-location`      % "compile->compile;multi-jvm->multi-jvm"
  )
  .enablePlugins(AutoMultiJvm, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.ConfigClient
  )

lazy val `csw-config-client-cli` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-config-client`,
    `csw-config-server` % "test->test",
    `csw-location`      % "multi-jvm->multi-jvm"
  )
  .enablePlugins(AutoMultiJvm, DeployApp, MaybeCoverage)
  .settings(
    libraryDependencies ++= Dependencies.CswConfigClientCli
  )

lazy val `csw-vslice` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-framework`,
    `csw-command`
  )
  .enablePlugins(DeployApp)

lazy val `csw-framework` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-messages`,
    `csw-config-client`,
    `csw-logging`,
    `csw-command`,
    `csw-location`      % "compile->compile;multi-jvm->multi-jvm",
    `csw-config-server` % "multi-jvm->test"
  )
  .enablePlugins(AutoMultiJvm, GenJavadocPlugin, CswBuildInfo, DeployApp)
  .settings(
    libraryDependencies ++= Dependencies.CswFramework
  )

lazy val `csw-command` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-messages`,
    `csw-logging`
  )
  .enablePlugins(AutoMultiJvm, GenJavadocPlugin)
  .settings(libraryDependencies ++= Dependencies.CswCommand)

lazy val `csw-commons` = project
  .settings(Common.projectSettings)
  .enablePlugins(PublishBintray, GenJavadocPlugin)
  .settings(
    libraryDependencies ++= Dependencies.CswCommons
  )

lazy val `csw-benchmark` = project
  .settings(Common.projectSettings)
  .dependsOn(
    `csw-logging`,
    `csw-messages`,
    `csw-framework` % "compile->compile;test->test",
    `csw-command`
  )
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies ++= Dependencies.Benchmark
  )

//Integration test project
lazy val integration = project
  .settings(Common.projectSettings)
  .dependsOn(`csw-location`, `csw-location-agent`)
  .enablePlugins(DeployApp)
  .settings(
    libraryDependencies ++= Dependencies.Integration
  )

//Docs project
lazy val docs = project.enablePlugins(ParadoxSite, NoPublish)
  .settings(Common.projectSettings)

//Example code
lazy val examples = project
  .settings(Common.projectSettings)
  .dependsOn(`csw-location`, `csw-config-client`, `csw-config-server` % "test->test", `csw-logging`, `csw-messages`)
  .enablePlugins(DeployApp)
  .settings(
    libraryDependencies ++= Dependencies.CswProdExamples
  )
