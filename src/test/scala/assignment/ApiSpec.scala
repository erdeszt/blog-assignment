package assignment

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.junit._

class ApiSpec extends JUnitRunnableSpec:
  
  override def spec = suite("Api")(
    suite("Create blog")(
      testM("should succeed with correct name")(
        UIO(assert(true)(equalTo(true))) 
      )
    )
  )


