package assignment.dto

import assignment.model.Blog

final case class CreatePostForBlog(
  blogId: Blog.Id,
  create: CreatePost,
)
