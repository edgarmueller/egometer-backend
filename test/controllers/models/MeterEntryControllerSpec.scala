package controllers.models

import com.mohiva.play.silhouette.test._
import play.api.libs.json._
import play.api.test._
import spec.{ApiSpecification, AuthSpec, MongoSpecification}

class MeterEntryControllerSpec
  extends AuthSpec
  with ApiSpecification
  with MongoSpecification {

  "MeterController" should {

    "find all meters of a user by date" in new AuthContext with MongoContext {

      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )

      new WithApplication(application) with MongoScope {

        val date = "2018-06-18"
        val meterId = "5b352e060d23cb43f5356301"

        val content: JsValue = contentAsJson(
          route(
            application,
            FakeRequest(GET, s"/api/v1/entries/$date").withAuthenticator(loginInfo)
          ).get
        )

        (content \ meterId).as[JsArray].value.size must beEqualTo(1)
      }
    }
  }

}
