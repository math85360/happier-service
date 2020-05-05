organization  := "com.iz2use"

name := "happier-service"

version in ThisBuild := "0.0.1"

scalaVersion in ThisBuild := "2.12.11"

ThisBuild / Compile / scalacOptions += "-Yrangepos"

scalacOptions in ThisBuild ++= Seq("-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-Ypartial-unification")

sources in (Compile, doc) in ThisBuild := Seq.empty

publishArtifact in (Compile, packageDoc) in ThisBuild := false

import sbt.Project.projectToRef

// include text identification for documents downloaded
lazy val frBricodepot = baseModule("fr/bricodepot")
    .settings(
        libraryDependencies ++= Seq(
            "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
            "com.iz2use" %% "react-components" % "0.0.8-SNAPSHOT"
        )
    )
lazy val frBricodepotRead = readModule("fr/bricodepot").dependsOn(frBricodepot)
lazy val frBricodepotStream = streamModule("fr/bricodepot").dependsOn(frBricodepot)
lazy val frCedeo = baseModule("fr/cedeo")
    .settings(
        libraryDependencies ++= Seq(
            "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
            "com.iz2use" %% "react-components" % "0.0.8-SNAPSHOT"
        )
    )
lazy val frCedeoRead = readModule("fr/cedeo").dependsOn(frCedeo)
lazy val frCedeoStream = streamModule("fr/cedeo").dependsOn(frCedeo)
    .settings(
        libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
        initialCommands in console += 
        """
            |import happier.fr.cedeo._
            |import happier.fr.cedeo.actor._
            |import happier.fr.cedeo.model._
            |implicit val browser = CedeoBrowser.createBrowser()
        """.stripMargin
    )
lazy val frEDF = baseModule("fr/edf")
lazy val frEDFRead = readModule("fr/edf").dependsOn(frEDF)
lazy val frEDFStream = streamModule("fr/edf").dependsOn(frEDF)
lazy val frIonos = baseModule("fr/ionos")
lazy val frIonosRead = readModule("fr/ionos").dependsOn(frIonos)
lazy val frIonosStream = streamModule("fr/ionos").dependsOn(frIonos)
lazy val frLeBonCoin = baseModule("fr/leboncoin")
    .settings(
        libraryDependencies ++= Seq(
            "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
            "com.iz2use" %% "react-components" % "0.0.8-SNAPSHOT"
        )
    )
lazy val frLeBonCoinRead = readModule("fr/leboncoin").dependsOn(frLeBonCoin)
lazy val frLeBonCoinStream = streamModule("fr/leboncoin").dependsOn(frLeBonCoin)
    .settings(
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
      initialCommands in console += 
      """
        |import happier.fr.leboncoin._
        |import happier.fr.leboncoin.actor._
        |import happier.fr.leboncoin.model._
        |implicit val browser = LeBonCoinBrowser.createBrowser()
      """.stripMargin
    )
lazy val frLeroyMerlin = baseModule("fr/leroymerlin")
lazy val frLeroyMerlinRead = readModule("fr/leroymerlin").dependsOn(frLeroyMerlin)
lazy val frLeroyMerlinStream = streamModule("fr/leroymerlin").dependsOn(frLeroyMerlin)
lazy val frMicrosoftOffice365 = baseModule("fr/microsoftoffice365")
lazy val frMicrosoftOffice365Read = readModule("fr/microsoftoffice365").dependsOn(frMicrosoftOffice365)
lazy val frMicrosoftOffice365Stream = streamModule("fr/microsoftoffice365").dependsOn(frMicrosoftOffice365)
lazy val frMisterBooking = baseModule("fr/misterbooking")
lazy val frMisterBookingRead = readModule("fr/misterbooking").dependsOn(frMisterBooking)
lazy val frMisterBookingStream = streamModule("fr/misterbooking").dependsOn(frMisterBooking)
lazy val frOrange = baseModule("fr/orange")
    .settings(
        libraryDependencies ++= Seq(
            "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
            "com.iz2use" %% "react-components" % "0.0.8-SNAPSHOT"
        )
    )
lazy val frOrangeRead = readModule("fr/orange").dependsOn(frOrange)
lazy val frOrangeStream = streamModule("fr/orange").dependsOn(frOrange)
lazy val frOVH = baseModule("fr/ovh")
lazy val frOVHRead = readModule("fr/ovh").dependsOn(frOVH)
lazy val frOVHStream = streamModule("fr/ovh").dependsOn(frOVH)
lazy val frSageOne = baseModule("fr/sageone")
lazy val frSageOneRead = readModule("fr/sageone").dependsOn(frSageOne)
lazy val frSageOneStream = streamModule("fr/sageone").dependsOn(frSageOne)
lazy val frSeLoger = baseModule("fr/seloger")
    .settings(
        libraryDependencies ++= Seq(
            "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
            "com.iz2use" %% "react-components" % "0.0.8-SNAPSHOT"
        )
    )
lazy val frSeLogerRead = readModule("fr/seloger").dependsOn(frSeLoger)
lazy val frSeLogerStream = streamModule("fr/seloger").dependsOn(frSeLoger)
lazy val frVmMateriaux = baseModule("fr/vmmateriaux")
    .settings(
        libraryDependencies ++= Seq(
            "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
            "com.iz2use" %% "react-components" % "0.0.8-SNAPSHOT"
        )
    )
lazy val frVmMateriauxRead = readModule("fr/vmmateriaux").dependsOn(frVmMateriaux)
lazy val frVmMateriauxStream = streamModule("fr/vmmateriaux").dependsOn(frVmMateriaux)
    .settings(
      libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
      initialCommands in console += 
      """
        |import happier.fr.vmmateriaux._
        |import happier.fr.vmmateriaux.actor._
        |implicit val browser = VmMateriauxBrowser.createBrowser()
      """.stripMargin
    )

lazy val allModules   = Seq[(Project, Project, Project)](
    (frBricodepot,frBricodepotRead,frBricodepotStream),
    (frCedeo,frCedeoRead,frCedeoStream),
    (frEDF,frEDFRead,frEDFStream),
    (frIonos,frIonosRead,frIonosStream),
    (frLeBonCoin,frLeBonCoinRead,frLeBonCoinStream),
    (frLeroyMerlin,frLeroyMerlinRead,frLeroyMerlinStream),
    (frMicrosoftOffice365,frMicrosoftOffice365Read,frMicrosoftOffice365Stream),
    (frMisterBooking,frMisterBookingRead,frMisterBookingStream),
    (frOrange,frOrangeRead,frOrangeStream),
    (frOVH,frOVHRead,frOVHStream),
    (frSageOne,frSageOneRead,frSageOneStream),
    (frSeLoger,frSeLogerRead,frSeLogerStream),
    (frVmMateriaux,frVmMateriauxRead,frVmMateriauxStream)
)

lazy val aggregatedProjectRefs  = for { 
    (a, b, c) <- allModules
    p <- Seq(a, b, c)
} yield p:ProjectReference

lazy val aggregatedProjects  = for { 
    (a, b, c) <- allModules
    p <- Seq(a, b, c)
} yield p:ClasspathDep[ProjectReference]

lazy val root = project.in(file("."))
  .settings(noPublishSettings)
  .aggregate(aggregatedProjectRefs: _*)
  .aggregate(core, read, stream)

lazy val all = project.in(file("all"))
  .settings(noPublishSettings)
  .settings(
      initialCommands in console := 
      """
        |import akka.actor.typed._
        |import akka.actor.typed.scaladsl._
        |import akka.stream._
        |import akka.stream.scaladsl._
        |import akka.stream.typed.scaladsl._
        |import akka.util.Timeout
        |import happier.actor._
        |import happier.api._
        |import happier.api.document._
        |import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
        |import org.slf4j.event.Level
        |import scala.concurrent.duration._
        |import akka.actor.typed.scaladsl.AskPattern._
        |
        |val behavior = Behaviors.logMessages(LogOptions().withLevel(Level.TRACE), Supervisor())
        |implicit val system = ActorSystem(behavior, "temp")
        |import system.executionContext
      """.stripMargin
  )
  .dependsOn(core, read, stream)
  .dependsOn(aggregatedProjects: _*)

lazy val read = module("read")
    .settings(
        libraryDependencies ++= Seq(
        "org.apache.poi" % "poi" % "4.1.0",
        "org.apache.pdfbox" % "pdfbox" % "2.0.13"))
    .dependsOn(core)

lazy val stream = module("stream")
    .settings(
        libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % Versions.akka,
        "com.typesafe.akka" %% "akka-slf4j" % Versions.akka,
        "com.typesafe.akka" %% "akka-stream" % Versions.akka,
        "com.typesafe.akka" %% "akka-stream-typed" % Versions.akka,
        "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka
        )
    )
    .dependsOn(core)

lazy val core = module("core")
    .settings(
        libraryDependencies ++= Seq(
        "net.ruippeixotog" %% "scala-scraper" % "2.1.0",
        "de.heikoseeberger" %% "akka-http-circe" % "1.31.0",
        "com.lihaoyi" %% "utest" % Versions.utest % "test",
        "io.circe" %% "circe-core" % Versions.circe,
        "io.circe" %% "circe-generic" % Versions.circe,
        "io.circe" %% "circe-parser" % Versions.circe,
        "io.circe" %% "circe-generic-extras" % Versions.circe,
        "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp)
    )

lazy val baseSettings = Seq()

lazy val readSettings = Seq()

lazy val streamSettings = Seq()

def module(path: String) = {
  val id = path.split("/").reduce( _ + _).split("-").reduce(_ + _.capitalize)
  Project(id, file(s"modules/$path"))
    .settings(
      testFrameworks += new TestFramework("utest.runner.Framework"),
      autoCompilerPlugins := true,
      organization  := "com.iz2use",
      name := path.split("/").reduce((a,b) => s"$a-$b"),
      publishTo := Some(iz2use.resolver),
      publishMavenStyle := false,
      addCompilerPlugin("org.scalamacros" % "paradise" % Versions.macroparadise cross CrossVersion.full),
    )
}

def baseModule(path: String) = module(s"$path/base")
    .settings(baseSettings)
    .dependsOn(core)
    
def readModule(path: String) = module(s"$path/read")
    .settings(readSettings)
    .dependsOn(read)
    
def streamModule(path: String) = {
    val m = path.dropWhile(_!='/').stripPrefix("/")
    module(s"$path/stream")
        .settings(streamSettings)
        .dependsOn(stream)
        .settings(
        libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
        initialCommands in console += 
        s"""
            |import akka.actor.typed._
            |import akka.actor.typed.scaladsl._
            |import akka.stream._
            |import akka.stream.scaladsl._
            |import akka.stream.typed.scaladsl._
            |import akka.util.Timeout
            |import happier.actor._
            |import happier.api._
            |import happier.api.document._
            |import net.ruippeixotog.scalascraper.browser.HtmlUnitBrowser
            |import org.slf4j.event.Level
            |import scala.concurrent.duration._
            |import akka.actor.typed.scaladsl.AskPattern._
            |
            |val behavior = Behaviors.logMessages(LogOptions().withLevel(Level.TRACE), Supervisor())
            |implicit val system = ActorSystem(behavior, "temp")
            |import system.executionContext
        """.stripMargin
        )
    }

lazy val noPublishSettings = Seq(
publish := {},
publishLocal := {},
publishArtifact := false
)

resolvers in ThisBuild ++= Seq(
    Resolver.sonatypeRepo("releases"),
    sbt.Resolver.bintrayRepo("denigma", "denigma-releases"),
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype OSS Release Staging" at "https://oss.sonatype.org/content/groups/staging",
    Resolver.jcenterRepo
    )

resolvers in ThisBuild += Resolver.url("iz2use", url("https://www.iz2use.com/maven2"))(Resolver.ivyStylePatterns)