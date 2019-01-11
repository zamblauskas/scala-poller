name := "scala-poller"

organization := "zamblauskas"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % "2.5.19",
  "org.scalatest"     %% "scalatest"    % "3.0.5"   % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.19"  % Test
)

licenses := ("MIT", url("https://opensource.org/licenses/MIT")) :: Nil
