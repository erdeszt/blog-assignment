val circeVersion  = "0.13.0"
val http4sVersion = "0.21.21"
val tapirVersion  = "0.17.9"
val zioVersion    = "1.0.5"

lazy val root = project
  .in(file("."))
  .settings(
    name := "blog-assignment",
    version := "0.1.0",
    scalaVersion := "2.13.5",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % "0.12.1",
      "mysql" % "mysql-connector-java" % "8.0.23",
      "org.flywaydb" % "flyway-core" % "7.7.2",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-zio-http4s-server" % tapirVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-refined" % circeVersion, // TODO: Remove
      "com.github.jatcwang" %% "hotpotato-core" % "0.1.1",
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-interop-cats" % "2.4.0.0",
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "dev.zio" %% "zio-test-junit" % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
