package assignment.dto

import assignment.model.Blog

// TODO: Move these into the Routes module
final case class CreateBlog(
    name:  Blog.Name,
    posts: List[CreatePost]
)
