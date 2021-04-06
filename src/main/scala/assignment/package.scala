import assignment.model._
import zio._

package object assignment {
  type RequestContext = Has[User]
}
