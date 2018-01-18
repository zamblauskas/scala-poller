name := "scala-poller"

organization := "zamblauskas"

version := "0.1.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % "2.5.9",
  "org.scalatest"     %% "scalatest"    % "3.0.4"  % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.9"  % Test
)
