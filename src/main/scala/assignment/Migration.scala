package assignment

import org.flywaydb.core.Flyway
import zio._

import java.sql.DriverManager

trait Migration {
  def migrate: UIO[Unit]
}

object Migration {

  final case class Live(config: DatabaseConfig) extends Migration {
    override def migrate: UIO[Unit] = {
      // TODO: Cleanup connection
      val connectionString = s"jdbc:mysql://${config.host.value}:${config.port.value}"
      UIO {
        val connection = DriverManager.getConnection(connectionString, config.user.value, config.password.value)
        val statement  = connection.createStatement()

        statement.executeUpdate(s"create database if not exists ${config.database.value}")

        val flyway = Flyway
          .configure()
          .dataSource(connectionString + "/" + config.database.value, config.user.value, config.password.value)
          .load()

        flyway.migrate()
      }
    }
  }

  val layer: URLayer[Has[DatabaseConfig], Has[Migration]] = ZLayer.fromService(Live)

  def migrate: URIO[Has[Migration], Unit] = ZIO.accessM(_.get.migrate)

}
