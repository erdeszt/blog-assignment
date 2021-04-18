package assignment.service

import assignment.{service, Queries}
import assignment.model.DomainError._
import assignment.model._
import cats.{Functor, Monoid}
import hotpotato._
import shapeless.Coproduct
import shapeless.ops.coproduct.Basis
import zio._
import zio.query.ZQuery

import scala.collection.mutable
import scala.collection.mutable.Builder

trait Api {
  def createBlog(
      name:  Blog.Name,
      slug:  Blog.Slug,
      posts: List[(Option[Post.Title], Post.Content)],
  ): IO[Api.CreateBlogError, (Blog.Id, List[Post.Id])]
  def createPost(blogId:  Blog.Id, title: Option[Post.Title], content: Post.Content): IO[Api.CreatePostError, Post.Id]
  def getBlogs(withPosts: WithPosts): UIO[List[Blog]]
  def getBlogById(blogId: Blog.Id, withPosts: WithPosts): IO[Api.GetBlogByIdError, Blog]
  def getBlogBySlug(slug: Blog.Slug, withPosts: WithPosts): IO[Api.GetBlogBySlugError, Blog]
}

object Api {

  type PostValidationError = OneOf2[EmptyPostTitle, EmptyPostContent]
  type CreateBlogError =
    OneOf6[EmptyBlogName, EmptyBlogSlug, InvalidBlogSlug, BlogSlugAlreadyExists, EmptyPostTitle, EmptyPostContent]
  type CreatePostError    = OneOf3[BlogNotFound, EmptyPostTitle, EmptyPostContent]
  type GetBlogByIdError   = BlogNotFound
  type GetBlogBySlugError = BlogSlugNotFound

  private val blogSlugFormat = "^[a-zA-Z][a-zA-Z0-9\\-]*$".r

  final case class Live(idProvider: IdProvider, blogStore: BlogStore, postStore: PostStore, trx: TransactionHandler)
      extends Api {

    private val trxLayer = ZLayer.succeed(trx)

    private def runQuery[E, A](query: ZQuery[Has[TransactionHandler], E, A]): IO[E, A] = {
      query.run.provideLayer(trxLayer)
    }

    override def createBlog(
        name:  Blog.Name,
        slug:  Blog.Slug,
        posts: List[(Option[Post.Title], Post.Content)],
    ): IO[CreateBlogError, (Blog.Id, List[Post.Id])] = {
      implicit val errorEmbedder = Embedder.make[CreateBlogError]
      for {
        _ <- ZIO.when(name.value.isEmpty)(ZIO.fail(EmptyBlogName().embed))
        _ <- ZIO.when(slug.value.isEmpty)(ZIO.fail(EmptyBlogSlug().embed))
        _ <- ZIO.unless(blogSlugFormat.matches(slug.value))(ZIO.fail(InvalidBlogSlug(slug).embed))
        _ <- ZIO.foreach_(posts) { case (title, content) => validatePost[CreateBlogError](title, content) }
        _ <- ZIO.whenM(runQuery(Queries.GetBlogs.bySlug(slug)).map(_.nonEmpty))(
          ZIO.fail(BlogSlugAlreadyExists(slug).embed),
        )
        blogId <- idProvider.generateId.map(Blog.Id)
        blogPosts <- ZIO.foreach(posts) {
          case (title, content) =>
            idProvider.generateId.map(Post.Id).map(PostStore.Create(_, blogId, title, content))
        }
        _ <- trx.run {
          for {
            _ <- blogStore.createBlog(blogId, name, slug)
            _ <- postStore.createPosts(blogPosts)
          } yield ()
        }
      } yield (blogId, blogPosts.map(_.id))
    }

    override def createPost(
        blogId:  Blog.Id,
        title:   Option[Post.Title],
        content: Post.Content,
    ): IO[CreatePostError, Post.Id] = {
      implicit val errorEmbedder = Embedder.make[CreatePostError]
      for {
        _ <- validatePost[CreatePostError](title, content)
        _ <- runQuery(Queries.GetBlogs.byId(blogId)).someOrFail(BlogNotFound(blogId).embed)
        id <- idProvider.generateId.map(Post.Id)
        _ <- trx.run(postStore.createPost(PostStore.Create(id, blogId, title, content)))
      } yield id
    }

    // TODO: PR for Iterable=>IterableOnce and isEmpty
    def foreachPar[R, E, A, B, Collection[+Element] <: IterableOnce[Element]](
        as: Collection[A],
    )(f:    A => ZQuery[R, E, B])(implicit bf: BuildFrom[Collection[A], B, Collection[B]]): ZQuery[R, E, Collection[B]] = {
      if (as.iterator.isEmpty) ZQuery.succeed(bf.newBuilder(as).result())
      else {
        val iterator = as.iterator
        var builder: ZQuery[R, E, Builder[B, Collection[B]]] = null
        while (iterator.hasNext) {
          val a = iterator.next()
          if (builder eq null) builder = f(a).map(bf.newBuilder(as) += _)
          else builder                 = builder.zipWithPar(f(a))(_ += _)
        }
        builder.map(_.result())
      }
    }

    private def getBlogWithPosts[Collection[+Element] <: IterableOnce[Element], E](
        blogQuery: ZQuery[Has[TransactionHandler], E, Collection[BlogStore.BlogRead]],
        withPosts: WithPosts,
    )(
        implicit F: Functor[Collection],
        bf:         BuildFrom[Collection[Blog.Id], List[Post], Collection[List[Post]]],
    ): IO[E, Collection[Blog]] = {
      runQuery {
        for {
          blogs <- blogQuery
          posts <- if (withPosts == WithPosts.Yes) {
            foreachPar(F.map(blogs)(_.id))(Queries.GetPostsByBlogId.query)
              .map(posts => Monoid[List[Post]].combineAll(posts).groupBy(_.blogId))
          } else {
            ZQuery.succeed(Map.empty[Blog.Id, List[Post]])
          }
        } yield F.map(blogs) { blog =>
          Blog(
            blog.id,
            blog.name,
            blog.slug,
            posts.getOrElse(blog.id, List.empty),
          )
        }
      }
    }

    // TODO: Move out
    private implicit def bf[I, O]: BuildFrom[Option[I], List[O], Option[List[O]]] = {
      new BuildFrom[Option[I], List[O], Option[List[O]]] {
        def fromSpecific(from: Option[I])(it: IterableOnce[List[O]]): Option[List[O]] =
          from.map(_ => it.iterator.toList.flatten)

        def newBuilder(from: Option[I]): mutable.Builder[List[O], Option[List[O]]] = {
          new mutable.Builder[List[O], Option[List[O]]] {
            var values:   Option[List[O]] = None
            def clear():  Unit            = values = None
            def result(): Option[List[O]] = values
            def addOne(elem: List[O]): this.type = {
              values match {
                case None        => values = Some(elem)
                case Some(elems) => values = Some(elem ++ elems)
              }
              this
            }
          }
        }
      }
    }

    override def getBlogs(withPosts: WithPosts): UIO[List[Blog]] = {
      getBlogWithPosts[List, Nothing](Queries.GetBlogs.all, withPosts)
    }

    override def getBlogById(blogId: Blog.Id, withPosts: WithPosts): IO[GetBlogByIdError, Blog] = {
      getBlogWithPosts[Option, Nothing](Queries.GetBlogs.byId(blogId), withPosts)
        .someOrFail(BlogNotFound(blogId))
    }

    override def getBlogBySlug(slug: Blog.Slug, withPosts: WithPosts): IO[Api.GetBlogBySlugError, Blog] = {
      getBlogWithPosts(Queries.GetBlogs.bySlug(slug), withPosts)
        .someOrFail(BlogSlugNotFound(slug))
    }

    def validatePost[E <: Coproduct](
        title:        Option[Post.Title],
        content:      Post.Content,
    )(implicit basis: Basis[E, PostValidationError]): IO[E, Unit] = {
      implicit val embedder = Embedder.make[PostValidationError]
      for {
        _ <- ZIO.when(title.exists(_.value.isEmpty))(ZIO.fail(basis.inverse(Right(EmptyPostTitle().embed))))
        _ <- ZIO.when(content.value.isEmpty)(ZIO.fail(basis.inverse(Right(EmptyPostContent().embed))))
      } yield ()
    }

  }

  val layer: URLayer[Has[IdProvider] with Has[BlogStore] with Has[PostStore] with Has[TransactionHandler], Has[Api]] =
    ZLayer.fromServices[IdProvider, BlogStore, PostStore, TransactionHandler, Api](Live)

  def createBlog(
      name:  Blog.Name,
      slug:  Blog.Slug,
      posts: List[(Option[Post.Title], Post.Content)],
  ): ZIO[Has[Api], CreateBlogError, (Blog.Id, List[Post.Id])] =
    ZIO.accessM(_.get.createBlog(name, slug, posts))

  def createPost(
      blogId:  Blog.Id,
      title:   Option[Post.Title],
      content: Post.Content,
  ): ZIO[Has[Api], Api.CreatePostError, Post.Id] = {
    ZIO.accessM(_.get.createPost(blogId, title, content))
  }

  def getBlogs(withPosts: WithPosts): ZIO[Has[Api], Nothing, List[Blog]] = {
    ZIO.accessM(_.get.getBlogs(withPosts))
  }

  def getBlogById(blogId: Blog.Id, withPosts: WithPosts): ZIO[Has[Api], Api.GetBlogByIdError, Blog] = {
    ZIO.accessM(_.get.getBlogById(blogId, withPosts))
  }

  def getBlogBySlug(slug: Blog.Slug, withPosts: WithPosts): ZIO[Has[Api], Api.GetBlogBySlugError, Blog] = {
    ZIO.accessM(_.get.getBlogBySlug(slug, withPosts))
  }

}
