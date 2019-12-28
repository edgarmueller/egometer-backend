package controllers.models

import com.mohiva.play.silhouette.test._
import models.meter.Meter
import play.api.libs.json._
import play.api.test._
import reactivemongo.bson.BSONObjectID
import spec.{ApiSpecification, AuthSpec, MongoSpecification}
import utils.auth.DefaultEnv

class MetersControllerSpec
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
            FakeRequest(GET, "/api/v1/meters").withAuthenticator(loginInfo)
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
          FakeRequest(POST, "/api/v1/meters")
            .withAuthenticator[DefaultEnv](loginInfo)
            .withJsonBody(Json.toJson(newMeter))
        ).get)

      content.as[Meter].userId must beSome(user.id)

      new WithApplication(application) with MongoScope {
        val allMeters: JsValue = contentAsJson(
          route(
            application,
            FakeRequest(GET, "/api/v1/meters").withAuthenticator(loginInfo)
          ).get
        )
        allMeters.as[JsArray].value.size must beEqualTo(1)
      }
    }

    "find a meter by id" in new AuthContext with MongoContext {
      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json")
      )

      new WithApplication(application) with MongoScope {
        val meter = contentAsJson(
          route(
            application,
            FakeRequest(GET, "/api/v1/meters/5b352e060d23cb43f5356301").withAuthenticator(loginInfo)
          ).get
        )
        meter.as[JsObject] \ "id" must beEqualTo(JsDefined(JsString("5b352e060d23cb43f5356301")))
      }
    }

    "update a meter by id" in new AuthContext with MongoContext {
      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json")
      )

      new WithApplication(application) with MongoScope {
        contentAsJson(route(
          application,
          FakeRequest(POST, "/api/v1/meters/5b352e060d23cb43f5356301")
            .withAuthenticator(loginInfo)
            .withBody(Json.obj(
              "schemaId" -> "5b352d1821d900141fc5ab03",
              "name" -> "Something else",
              "widget" ->"Bars",
              "color" -> "#238cd8",
              "userId" -> "a8a0c0ba-74aa-4bce-862d-08637ef7ba78"
            ))
        ).get)
        val meter: JsValue = contentAsJson(
          route(
            application,
            FakeRequest(GET, "/api/v1/meters/5b352e060d23cb43f5356301").withAuthenticator(loginInfo)
          ).get
        )
        meter.as[JsObject] \ "name" must beEqualTo(JsDefined(JsString("Something else")))
      }
    }

    "delete a meter by id" in new AuthContext with MongoContext {
      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json")
      )

      new WithApplication(application) with MongoScope {
        contentAsJson(
          route(
            application,
            FakeRequest(DELETE, "/api/v1/meters/5b352e060d23cb43f5356301").withAuthenticator(loginInfo)
          ).get
        )
        route(
          application,
          FakeRequest(GET, "/api/v1/meters/5b352e060d23cb43f5356301").withAuthenticator(loginInfo)
        ) must beSome.which(status(_) == NOT_FOUND)
      }
    }
  }
}
