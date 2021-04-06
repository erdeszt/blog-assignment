## Blog assignment

TODO:
* Option[NonEmptyString] for post.title?
* Logging
* WebSpec
* Scapegoat
* Auth?
* TODO: Consider moving id provider to store
* [BUG]Race condition in existing slug check


### Build, run & test:

```shell
> sbt compile
> sbt test
> DB_HOST=localhost \ 
  DB_PORT=3306 \
  DB_DATABASE=assignment \
  DB_USER=root \
  DB_PASSWORD=root \
  sbt run
```

#### Api documentation:

[http://localhost:8080/docs]()