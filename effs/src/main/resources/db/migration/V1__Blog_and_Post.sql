create table blog (
  id varchar(36) primary key,
  name varchar(255) not null,
  slug varchar(255) not null,
  created_at timestamp not null default now(),
  constraint blog_slug_unique unique(slug)
);

create table post (
  id varchar(36) primary key,
  blog_id varchar(36),
  title varchar(255) null,
  content text not null,
  view_count int not null default 0,
  created_at timestamp not null default now(),

  constraint post_blog_id_fk foreign key (blog_id) references blog(id) on delete cascade
);
