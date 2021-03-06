package assignment

import zio._
import zio.system.System

final case class DatabaseConfig(
    host:     DatabaseConfig.Host,
    port:     DatabaseConfig.Port,
    database: DatabaseConfig.Database,
    user:     DatabaseConfig.User,
    password: DatabaseConfig.Password,
)

object DatabaseConfig {
  final case class Host(value:     String) extends AnyVal
  final case class Port(value:     Int) extends AnyVal
  final case class Database(value: String) extends AnyVal
  final case class User(value:     String) extends AnyVal
  final case class Password(value: String) extends AnyVal

  final case class MissingDatabaseConfigKeyError(key: String) extends Exception(s"Missing database config key: ${key}")

  private val DB_HOST     = "DB_HOST"
  private val DB_PORT     = "DB_PORT"
  private val DB_DATABASE = "DB_DATABASE"
  private val DB_USER     = "DB_USER"
  private val DB_PASSWORD = "DB_PASSWORD"

  val layer: URLayer[System, Has[DatabaseConfig]] = ZLayer.fromServiceM { system =>
    val config = for {
      host <- system.env(DB_HOST).someOrFail(MissingDatabaseConfigKeyError(DB_HOST))
      port <- system
        .env(DB_PORT)
        .someOrFail(MissingDatabaseConfigKeyError(DB_PORT))
        .flatMap(raw => ZIO.effect(raw.toInt))
      database <- system.env(DB_DATABASE).someOrFail(MissingDatabaseConfigKeyError(DB_DATABASE))
      user <- system.env(DB_USER).someOrFail(MissingDatabaseConfigKeyError(DB_USER))
      password <- system.env(DB_PASSWORD).someOrFail(MissingDatabaseConfigKeyError(DB_PASSWORD))
    } yield DatabaseConfig(Host(host), Port(port), Database(database), User(user), Password(password))

    config.orDie
  }
}
