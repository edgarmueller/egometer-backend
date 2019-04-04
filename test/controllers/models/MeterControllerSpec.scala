package controllers.models

import com.mohiva.play.silhouette.test._
import models.Meter
import play.api.libs.json._
import play.api.test._
import reactivemongo.bson.BSONObjectID
import spec.{ApiSpecification, AuthSpec, MongoSpecification}
import utils.auth.DefaultEnv

class MeterControllerSpec
  extends AuthSpec
    with ApiSpecification
    with MongoSpecification {

  import models.JsonFormats._
  "MeterController" should {

    "find all meters of a user" in new AuthContext with MongoContext {

      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json")
      )

      new WithApplication(application) with MongoScope {

        val content: JsValue = contentAsJson(
          route(
            application,
            FakeRequest(GET, "/meters").withAuthenticator(loginInfo)
          ).get
        )

        content.as[JsArray].value.size must beEqualTo(1)
      }
    }

    "create a meter for an user" in new AuthContext with MongoContext {
      val newMeter = Meter(
        Some(BSONObjectID.generate()),
        "5ac9065704040014c266ceb3",
        "New mood meter",
        widget = "whatever",
        color = "#aaa",
        None,
        None,
        None,
        None
      )
      val content: JsValue = contentAsJson(
        route(
          application,
          FakeRequest(POST, "/meters")
            .withAuthenticator[DefaultEnv](loginInfo)
            .withJsonBody(Json.toJson(newMeter))
        ).get)

      content.as[Meter].userId must beSome(user.id)


      val allMeters: JsValue = contentAsJson(
        route(
          application,
          FakeRequest(GET, "/meters").withAuthenticator(loginInfo)
        ).get
      )
      allMeters.as[JsArray].value.size must beEqualTo(1)
    }

    "delete all entries if meter is deleted" in new AuthContext with MongoContext {

      override val fixtures = Map(
        "meters" -> Seq("models/daos/meters/meters.json"),
        "entries" -> Seq("models/daos/entries/entries.json")
      )

      val date = "2018-06-18"
      val meterId = "5b352e060d23cb43f5356301"

      val content: JsValue = contentAsJson(
        route(
          application,
          FakeRequest(GET, s"/entries/$date").withAuthenticator(loginInfo)
        ).get
      )

      content must beEqualTo(Json.obj())
    }
  }
}
