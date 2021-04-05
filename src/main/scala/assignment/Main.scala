package assignment

import zio._

/**
  * TODO:
  *    - Setup server
  *    - Routing
  *    - Logging
  *    - ?Auth?
  *    - Tests
  *    - Docs
  *    - Dockerize
  *    - Setup CI
  */
object Main extends zio.App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val app = Migration.migrate *> zio.console.putStrLn("Hello")
    val dbConfig = DatabaseConfig(
      DatabaseConfig.Host("localhost"),
      DatabaseConfig.Port(3306),
      DatabaseConfig.Database("assignment"),
      DatabaseConfig.User("root"),
      DatabaseConfig.Password("root")
    )

    app.provideSomeLayer[ZEnv](ZLayer.succeed(dbConfig) >>> Migration.layer).exitCode
  }

}
