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
import org.atnos.eff.addon.cats.effect.IOEffect
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.implicits._
import shapeless.ops.coproduct.Unifier
import shapeless.Coproduct

import java.util.UUID

object Routes extends CirceEntityDecoder with CirceEntityEncoder {
  final case class ErrorResponse(
      code:    Int,
      message: String,
  )

  implicit val errorEncoder = deriveCodec[ErrorResponse]

  def evaluate[E, A](trx: Transactor[IO])(
      effect:             Eff[Fx.fx5[IO, IdProviderOp, BlogStoreOp, PostStoreOp, Either[E, *]], A],
  ): IO[Either[E, A]] = {
    IOEffect.to(
      either.runEither(
        PostStore.evalPostStore(trx)(
          BlogStore.evalBlogStore(trx)(
            IdProvider.evalIdProvider(effect),
          ),
        ),
      ),
    )
  }

  def run[E <: Coproduct, A](trx: Transactor[IO])(
      effect:                     Eff[Fx.fx5[IO, IdProviderOp, BlogStoreOp, PostStoreOp, Either[E, *]], A],
  )(implicit unifier:             Unifier.Aux[E, DomainError]): Runner[E, A] = new Runner(trx, effect, unifier)

  class Runner[E <: Coproduct, A](
      trx:     Transactor[IO],
      effect:  Eff[Fx.fx5[IO, IdProviderOp, BlogStoreOp, PostStoreOp, Either[E, *]], A],
      unifier: Unifier.Aux[E, DomainError],
  ) {
    def toResponse(implicit encoder: EntityEncoder[IO, A]): IO[Response[IO]] = to(identity)
    def to[B: EntityEncoder[IO, *]](transform: A => B): IO[Response[IO]] = {
      evaluate[E, A](trx)(effect).map(_.leftMap(unifier(_))).flatMap {
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
