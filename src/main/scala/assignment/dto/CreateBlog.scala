package assignment.dto

import assignment.model.Blog

final case class CreateBlog(
  name: Blog.Name,
  posts: List[CreatePost],
)
