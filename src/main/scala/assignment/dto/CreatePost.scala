package assignment.dto

import assignment.model.Post


final case class CreatePost(
  title: Option[Post.Title],
  body: Post.Body,
)
