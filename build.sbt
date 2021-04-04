lazy val root = project
  .in(file("."))
  .settings(
    name := "blog-assignment",
    version := "0.1.0",

    scalaVersion := "3.0.0-M1",

    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % "0.12.1",
      "mysql" % "mysql-connector-java" % "8.0.23",
      "org.xerial" % "sqlite-jdbc" % "3.34.0" % Test,
    
      "org.http4s" %% "http4s-core" % "1.0.0-M20",
      "org.http4s" %% "http4s-dsl" % "1.0.0-M20",
      "org.http4s" %% "http4s-circe" % "1.0.0-M20",
      "org.http4s" %% "http4s-blaze-server" % "1.0.0-M20",
      
      "dev.zio" %% "zio" % "1.0.5",
      "dev.zio" %% "zio-test" % "1.0.5" % Test,
    ).map(_.withDottyCompat(scalaVersion.value)),
    evictionErrorLevel := sbt.util.Level.Warn,
  )
