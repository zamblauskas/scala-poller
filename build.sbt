name := "scala-poller"

organization := "zamblauskas"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % "2.6.3",
  "org.scalatest"     %% "scalatest"    % "3.1.0"   % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.6.3"  % Test
)

licenses := ("MIT", url("https://opensource.org/licenses/MIT")) :: Nil
