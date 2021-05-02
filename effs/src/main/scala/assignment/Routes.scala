package assignment

import assignment.dto._
import assignment.model._
import assignment.service._
import assignment.service.Api.CreatePostError
import cats.effect.IO
import cats.syntax.either._
import doobie._
import io.circe.generic.semiauto.deriveCodec
import org.atnos.eff._
import org.atnos.eff.syntax.either._
import org.atnos.eff.addon.cats.effect.IOEffect
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.implicits._
import shapeless.ops.coproduct.Unifier
import shapeless.Coproduct

object Routes extends CirceEntityDecoder with CirceEntityEncoder {

  type App[E <: Coproduct, A] =
    Eff[Fx.fx6[IO, IdProvider.Op, BlogStore.Op, PostStore.Op, TransactionHandler.Op, Either[E, *]], A]

  final case class ErrorResponse(
      code:    Int,
      message: String,
  )

  implicit val errorEncoder = deriveCodec[ErrorResponse]

  def run[E <: Coproduct, A](trx: Transactor[IO])(
      effect:                     App[E, A],
  )(implicit unifier:             Unifier.Aux[E, DomainError]): Runner[E, A] = new Runner(trx, effect, unifier)

  class Runner[E <: Coproduct, A](
      trx:     Transactor[IO],
      effect:  App[E, A],
      unifier: Unifier.Aux[E, DomainError],
  ) {
    def evaluate: IO[Either[E, A]] = {
      IOEffect.to {
        effect.runIdProvider.runBlogStore.runPostStore
          .runTransactionHandler(trx)
          .runEither[E]
      }
    }
    def toResponse(implicit encoder: EntityEncoder[IO, A]): IO[Response[IO]] = to(identity)
    def to[B: EntityEncoder[IO, *]](transform: A => B): IO[Response[IO]] = {
      evaluate.map(_.leftMap(unifier(_))).flatMap {
        case Left(error) =>
          error match {
            case DomainError.BlogNotFound(blogId) => BadRequest(ErrorResponse(1, s"Blog: `${blogId.value}` not found"))
            case _                                => InternalServerError(ErrorResponse(666, s"Error not handled: ${error}"))
          }
        case Right(ok) => Ok(transform(ok))
      }
    }
  }

  def create(trx: Transactor[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / "blog" / UUIDVar(blogId) =>
      for {
        post <- request.as[CreatePostRequest]
        response <- run[CreatePostError, Post.Id](trx)(Api.createPost(Blog.Id(blogId), post.title, post.content)).to(
          CreatePostResponse(_),
        )
      } yield response
  }
}
