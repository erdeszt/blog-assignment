package assignment

import org.flywaydb.core.Flyway
import zio._

import java.sql.DriverManager

trait Migration {
  def migrate(): UIO[Unit]
}

object Migration {

  final case class Live(config: DatabaseConfig) extends Migration {
    override def migrate(): UIO[Unit] = {
      // TODO: Cleanup connection
      UIO {
        val connection = DriverManager.getConnection(config.host.value, config.user.value, config.password.value)
        val statement  = c.createStatement()

        statement.executeUpdate(s"create database if not exists ${config.database.value}")

        val flyway = Flyway
          .configure()
          .dataSource(config.host.value + "/" + config.database.value, config.user.value, config.password.value)
          .load()

        flyway.migrate()
      }
    }
  }

  val layer: URLayer[Has[DatabaseConfig], Has[Migration]] = ZLayer.fromService(Live)

}
