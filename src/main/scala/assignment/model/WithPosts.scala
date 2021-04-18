package assignment.model

sealed trait WithPosts

object WithPosts {
  case object Yes extends WithPosts
  case object No extends WithPosts
}
