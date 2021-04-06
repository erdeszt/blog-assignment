## Blog assignment

TODO:
* Slug as input
* Query rest binding
* Docker
* Scapegoat
* Github workflow


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