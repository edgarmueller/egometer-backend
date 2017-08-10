name := "egometer"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

val reactiveMongoVer = "0.13.0-play26"

resolvers += Resolver.jcenterRepo

resolvers += "emueller-bintray" at "http://dl.bintray.com/emueller/maven"

libraryDependencies ++= Seq(
  guice,
  ehcache,
  filters,
  "org.reactivemongo"      %% "play2-reactivemongo" % reactiveMongoVer,
  "io.swagger"             %% "swagger-play2"       % "1.6.0",
  "org.webjars"            %  "swagger-ui"          % "3.2.2",
  "org.scalatestplus.play" %% "scalatestplus-play"  % "3.1.1" % Test,
  "com.eclipsesource"      %% "play-json-schema-validator" % "0.9.5-M4",
  "com.github.nscala-time" %% "nscala-time"         % "2.18.0",
  "com.iheart"             %% "ficus"               % "1.4.1",
  "net.codingwell"         %% "scala-guice"         % "4.1.0",
  "com.enragedginger"      %% "akka-quartz-scheduler" % "1.6.1-akka-2.5.x",
  "com.typesafe.play"      %% "play-mailer"         % "6.0.1",
  "com.typesafe.play"      %% "play-mailer-guice"   % "6.0.1",
  "com.typesafe.play"      %% "play-specs2"          % "2.6.17" % "test",
  "org.specs2"             %% "specs2-core"          % "3.8.9" % "test",
  "org.specs2"             %% "specs2-matcher-extra" % "3.8.9" % "test",
  "org.specs2"             %% "specs2-mock"          % "3.8.9" % "test",
  "de.flapdoodle.embed"    % "de.flapdoodle.embed.mongo" % "2.0.0" % "test",
  "com.typesafe.akka"      %% "akka-testkit" % "2.5.4" % "test"
)

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "5.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "5.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "5.0.0" % "test",
  "com.mohiva" %% "play-silhouette-persistence-reactivemongo" % "5.0.1"
)

import play.sbt.routes.RoutesKeys

RoutesKeys.routesImport += "play.modules.reactivemongo.PathBindables._"

routesGenerator := InjectedRoutesGenerator

routesImport += "utils.route.Binders._"

// https://github.com/playframework/twirl/issues/105
TwirlKeys.templateImports := Seq()

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  //"-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen", // Warn when numerics are widened.
  // Play has a lot of issues with unused imports and unsued params
  // https://github.com/playframework/playframework/issues/6690
  // https://github.com/playframework/twirl/issues/105
  "-Xlint:-unused,_"
)

javaOptions in Test += "-Dconfig.file=conf/application.test.conf"
