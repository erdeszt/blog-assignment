package assignment

import zio._

/**
 * TODO: 
 *    - Setup server
 *    - Routing
 *    - Logging
 *    - ?Auth?
 */
object Main extends zio.App:
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    UIO(println("Server...")).exitCode

