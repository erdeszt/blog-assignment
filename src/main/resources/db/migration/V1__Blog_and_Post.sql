create table blog (
    id varchar(36) primary key,
    name varchar(255) not null,
    slug varchar(255) not null,
    created_at timestamp not null default now()
);

create table post (
    id varchar(36) primary key,
    blog_id varchar(36),
    title varchar(255) null,
    body text not null,
    created_at timestamp not null default now()

    -- TODO: Foreign key constraint
);
