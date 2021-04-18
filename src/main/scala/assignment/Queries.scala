package assignment

import assignment.model._
import assignment.service.{BlogStore, TransactionHandler, UUIDDatabaseMapping}
import cats.data.NonEmptyList
import doobie._
import doobie.implicits._
import zio._
import zio.query._

object Queries extends UUIDDatabaseMapping {

  final case class GetPostsByBlogId(blogId: Blog.Id) extends Request[Nothing, List[Post]]

  object GetPostsByBlogId {
    def query(blogId: Blog.Id): ZQuery[Has[TransactionHandler], Nothing, List[Post]] = {
      ZQuery.fromRequest(GetPostsByBlogId(blogId))(dataSource)
    }

    val dataSource: DataSource.Batched[Has[TransactionHandler], GetPostsByBlogId] =
      new DataSource.Batched[Has[TransactionHandler], GetPostsByBlogId] {
        val identifier: String = "GetPostsByBlogIdDataSource"
        def run(requests: Chunk[GetPostsByBlogId]): ZIO[Has[TransactionHandler], Nothing, CompletedRequestMap] = {
          val resultMap = CompletedRequestMap.empty

          NonEmptyList.fromList(requests.toList) match {
            case None => ZIO.succeed(resultMap)
            case Some(NonEmptyList(request, Nil)) =>
              TransactionHandler
                .run {
                  sql"select id, blog_id, title, content, view_count from post where blog_id = ${request.blogId}"
                    .query[Post]
                    .to[List]
                }
                .map(posts => resultMap.insert(request)(Right(posts)))
            case Some(requests) =>
              val blogIds = requests.map(_.blogId)
              TransactionHandler
                .run {
                  (sql"select id, blog_id, title, content, view_count from post where " ++ Fragments.in(
                    fr"blog_id",
                    blogIds,
                  )).query[Post]
                    .to[List]
                }
                .map(_.groupBy(_.blogId).foldLeft(resultMap) {
                  case (map, (blogId, posts)) =>
                    map.insert(GetPostsByBlogId(blogId))(Right(posts))
                })
          }
        }
      }
  }

  sealed trait GetBlogRequest[Container[_]] extends Request[Nothing, Container[BlogStore.BlogRead]]
  final case class GetAllBlogs() extends GetBlogRequest[List]
  final case class GetBlogById(id:     Blog.Id) extends GetBlogRequest[Option]
  final case class GetBlogBySlug(slug: Blog.Slug) extends GetBlogRequest[Option]

  object GetBlogs {
    def all: ZQuery[Has[TransactionHandler], Nothing, List[BlogStore.BlogRead]] = {
      ZQuery.fromRequest(GetAllBlogs())(listDataSource)
    }
    def byId(id: Blog.Id): ZQuery[Has[TransactionHandler], Nothing, Option[BlogStore.BlogRead]] = {
      ZQuery.fromRequest(GetBlogById(id))(optionDataSource)
    }
    def bySlug(slug: Blog.Slug): ZQuery[Has[TransactionHandler], Nothing, Option[BlogStore.BlogRead]] = {
      ZQuery.fromRequest(GetBlogBySlug(slug))(optionDataSource)
    }

    val listDataSource: DataSource.Batched[Has[TransactionHandler], GetBlogRequest[List]] =
      new DataSource.Batched[Has[TransactionHandler], GetBlogRequest[List]] {
        val identifier: String = "GetBlogDataSource"
        def run(
            requests: Chunk[GetBlogRequest[List]],
        ): ZIO[Has[TransactionHandler], Nothing, CompletedRequestMap] = {
          val requestMap = CompletedRequestMap.empty
          requests.toList match {
            case Nil => ZIO.succeed(requestMap)
            case _ =>
              TransactionHandler
                .run(sql"select id, name, slug from blog".query[BlogStore.BlogRead].to[List])
                .map(blogs => requestMap.insert(GetAllBlogs())(Right(blogs)))
          }
        }
      }

    val optionDataSource: DataSource.Batched[Has[TransactionHandler], GetBlogRequest[Option]] =
      new DataSource.Batched[Has[TransactionHandler], GetBlogRequest[Option]] {
        val identifier: String = "GetBlogDataSource"
        def run(requests: Chunk[GetBlogRequest[Option]]): ZIO[Has[TransactionHandler], Nothing, CompletedRequestMap] = {
          val (getByIds, getBySlugs) = requests.toList.foldLeft((List.empty[GetBlogById], List.empty[GetBlogBySlug])) {
            case ((byIds, bySlugs), request) =>
              request match {
                case byId @ GetBlogById(_)     => (byId :: byIds, bySlugs)
                case bySlug @ GetBlogBySlug(_) => (byIds, bySlug :: bySlugs)
              }
          }

          runQueries[Blog.Id, GetBlogById](CompletedRequestMap.empty)(
            fr"id",
            _.id,
            _.id,
          )(getByIds).flatMap { requestMap =>
            runQueries[Blog.Slug, GetBlogBySlug](requestMap)(
              fr"slug",
              _.slug,
              _.slug,
            )(getBySlugs)
          }
        }
        private def runQueries[V, T <: Request[Nothing, Option[BlogStore.BlogRead]]](
            requestMap: CompletedRequestMap,
        )(
            field:      Fragment,
            destructor: T => V,
            selector:   BlogStore.BlogRead => V,
        )(requests:     List[T])(implicit put: Put[V]): ZIO[Has[TransactionHandler], Nothing, CompletedRequestMap] = {
          NonEmptyList.fromList(requests) match {
            case None => ZIO.succeed(requestMap)
            case Some(filterValues) =>
              TransactionHandler
                .run {
                  (sql"select id, name, slug from blog where " ++ Fragments.in(field, filterValues.map(destructor)))
                    .query[BlogStore.BlogRead]
                    .to[List]
                }
                .map { blogs =>
                  val blogLookup = blogs.map(blog => (selector(blog), blog)).toMap

                  requests.foldLeft(requestMap) {
                    case (map, request) =>
                      map.insert(request)(Right(blogLookup.get(destructor(request))))
                  }
                }
          }
        }
      }
  }

}
