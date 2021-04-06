package assignment.service

import assignment.DatabaseConfig
import org.flywaydb.core.Flyway
import zio._

import java.sql.{Connection, DriverManager}

trait Migration {
  def migrate: UIO[Unit]
}

object Migration {

  final case class Live(config: DatabaseConfig) extends Migration {
    override def migrate: UIO[Unit] = {
      createDatabase *> runMigration
    }

    private def connectionString(config: DatabaseConfig): String = {
      s"jdbc:mysql://${config.host.value}:${config.port.value}"
    }

    private def runMigration: UIO[Unit] = {
      UIO {
        val flyway = Flyway
          .configure()
          .dataSource(
            connectionString(config) + "/" + config.database.value,
            config.user.value,
            config.password.value,
          )
          .load()

        flyway.migrate()
      }
    }

    private def createDatabase: UIO[Unit] = {
      val createStatement = s"create database if not exists ${config.database.value}"
      ZManaged
        .fromAutoCloseable(
          UIO(DriverManager.getConnection(connectionString(config), config.user.value, config.password.value)),
        )
        .use { connection =>
          ZManaged
            .fromAutoCloseable(UIO(connection.createStatement()))
            .use(statement => UIO(statement.execute(createStatement)))
        }
    }
  }

  val layer: URLayer[Has[DatabaseConfig], Has[Migration]] = ZLayer.fromService(Live)

  def migrate: URIO[Has[Migration], Unit] = ZIO.accessM(_.get.migrate)

}
