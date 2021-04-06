## Blog assignment

### TODO:
* Write documentation
* Logging
* WebSpec
* Lint
    * Scapegoat doesn't work with current sbt version(try downgrade?)
    * Wartremover also doesn't work properly
* Consider moving id provider to store
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
  JWT_SECRET=secret \
  sbt run
```

#### Api documentation:

[http://localhost:8080/docs]()