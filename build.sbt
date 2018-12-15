name := "scalax-workshop"
organization in ThisBuild := "io.github.jeremyrsmith"
version in ThisBuild := "0.1.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.7"

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.3",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  ),
  scalacOptions ++= Seq(
    "-Ypartial-unification",
    "-language:experimental.macros",
    "-language:higherKinds"
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8")
)

val `support-macros` = project.settings(
  commonSettings,
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
)

val `scalax-workshop` = (project in file("."))
  .settings(commonSettings)
  .dependsOn(`support-macros`)

