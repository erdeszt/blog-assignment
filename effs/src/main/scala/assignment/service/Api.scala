package assignment.service

import assignment.dto.CreatePostRequest
import assignment.model.DomainError._
import assignment.model._
import assignment.service.IdProvider._idProvider
import assignment.service.BlogStore._blogStore
import assignment.service.PostStore._postStore
import assignment.service.TransactionHandler._trx
import cats.effect.IO
import hotpotato._
import org.http4s.dsl.io._
import cats.syntax.either._
import doobie.Transactor
import org.atnos.eff._
import org.http4s.HttpRoutes
import shapeless.Coproduct
import shapeless.ops.coproduct.Basis

object Api {

  type PostValidationError = OneOf2[EmptyPostTitle, EmptyPostContent]
  type CreateBlogError =
    OneOf6[EmptyBlogName, EmptyBlogSlug, InvalidBlogSlug, BlogSlugAlreadyExists, EmptyPostTitle, EmptyPostContent]
  type CreatePostError = OneOf3[BlogNotFound, EmptyPostTitle, EmptyPostContent]

  private val blogSlugFormat = "^[a-zA-Z][a-zA-Z0-9\\-]*$".r

  type _createPostError[R] = Either[CreatePostError, *] |= R

  def createPost[R: _idProvider: _blogStore: _postStore: _createPostError](
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  ): Eff[R, Post.Id] = {
    implicit val embedder = Embedder.make[CreatePostError]
    for {
      _ <- either.fromEither(validatePost(title, content).leftMap(widenError[CreatePostError](_)))
      _ <- BlogStore.getById(blogId).flatMap(either.optionEither(_, BlogNotFound(blogId).embed))
      id <- IdProvider.generateId.map(Post.Id)
      // TODO: EffT?
      _ <- PostStore.createPost(PostStore.Create(id, blogId, title, content))
    } yield id
  }

  def validatePost(
      title:   Option[Post.Title],
      content: Post.Content,
  ): Either[PostValidationError, Unit] = {
    implicit val embedder = Embedder.make[PostValidationError]
    for {
      _ <- Either.cond(title.exists(_.value.isEmpty), (), EmptyPostTitle().embed)
      _ <- Either.cond(content.value.isEmpty, (), EmptyPostContent().embed)
    } yield ()
  }

  def widenError[Sup <: Coproduct]: Widener[Sup] = new Widener[Sup]

  class Widener[Sup <: Coproduct] {
    def apply[Sub <: Coproduct](narrow: Sub)(implicit basis: Basis[Sup, Sub]): Sup = {
      basis.inverse(Right(narrow))
    }
  }

}
