## Blog assignment

### Build, run & test:

*Needs a running mysql server*

```shell
> sbt compile
> sbt test
> DB_HOST=localhost \ 
  DB_PORT=3306 \
  DB_DATABASE=assignment \
  DB_USER=root \
  DB_PASSWORD=root \
  JWT_SECRET=secret \
  sbt run
```

### Interactive API documentation:

[http://localhost:8080/docs]()

### Tour of the code:

The best place to start reading the code is with the [Api](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/main/scala/assignment/service/Api.scala#L10) interface.
It describes the available API calls with inputs, outputs and possible errors.
The file also contains the implementation of the business logic of the app. The relevant models are located in the [assignment.model](https://github.com/erdeszt/blog-assignment/tree/doc-links/src/main/scala/assignment/model) package.

The tests in [ApiSpec](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/test/scala/assignment/ApiSpec.scala#L60) describe the usage and behaviour of the api.

The http endpoints are defined in [Routes](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/main/scala/assignment/Routes.scala#L39). This is also where the OpenAPI documentation is generated and the endpoints are connected to the service layer(and domain errors are mapped to http responses).

The dependencies are setup in [Layers](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/main/scala/assignment/Layers.scala) and the server is started up in [Main](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/main/scala/assignment/Main.scala)

The database schema is defined in [src/main/resources/db/migration](https://github.com/erdeszt/blog-assignment/tree/doc-links/src/main/resources/db/migration)

The list of supported queries is defined in [Query](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/main/scala/assignment/model/Query.scala#L3) and examples of the json format are located in the [QuerySerializationSpec](https://github.com/erdeszt/blog-assignment/blob/doc-links/src/test/scala/assignment/QuerySerializationSpec.scala#L22)

### Questions:

* What were some of the reasons you chose the technology stack that you did?
    * Familiarity with the language and the libraries
    * Scala has great type system and language features that enable precise definition of the domain and the business logic
    * The [tapir](https://tapir.softwaremill.com/en/latest/) library is an awesome way to define http endpoints and easily generate client, server and documentation for them
* What were some of the tradeoffs you made when building this application? Why were these acceptable tradeoffs?
    * I didn't choose my libraries based on performance(although they shouldn't be terribly slow), I choose purely functional libraries because I prefer that style of coding
    * I didn't fully separate myself from the database type so switching to a different one would require some work, but the business logic is database agnostic so it should not require architectural changes just a new implementation of the storage interface
        * The ORM situation is not great in scala and I actually like the simpler data mapper approach provided by [doobie](https://tpolecat.github.io/doobie/)
        * Another possibility that I only realized while writing up these answers would have been [quill](https://github.com/getquill/quill) which would have worked fine for the simple queries that I have now but it gets messy for large joins
    * I didn't fix a race condition when checking for duplicate blog slugs, it would make the code a bit more complicated and there's a unique constraint in the database schema anyway it just means that the api produces a `500` response instead of `400`
    * I think these tradeoffs were acceptable given the constraints
* Given more time, what improvements or optimizations would you want to add
  later?
    * I would like to finish the `WebSpec` it's only missing a couple hours of work and could run integration tests against the server running inside docker during CI
    * I wanted to setup linting but I've chosen a very recent sbt version and there's no compatible releases yet
    * I also wanted to add request and service level logs but didn't have time
    * There should be a docker-compose setup for the development and test mysql databases but I'm on Windows right now so I didn't bother doing it.
    * The tests could run in parallel with a bit of work which would be nice.
* What would you need to do to make this application scale to hundreds of thousands of users?
    * The app scales horizontally with the current setup so for a while adding more servers is the easiest way to scale.
    At some point the database will become the bottleneck which can be improved by asynchronous writes, read replicas, caching or moving to nosql storage, dedicated search api/storage
* How would you change the architecture to allow that models are stored in different databases? E.g. posts are stored in Cassandra and blogs are stored in Postgres.
    * That's already possible with the current architecture.