package controllers.models

import java.util.UUID

import com.mohiva.play.silhouette.test._
import models.entry.{MeterEntriesByMeterDto, MeterEntryDto}
import models.meter.MeterDto
import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json._
import play.api.test._
import spec.{ApiSpecification, AuthSpec, MongoSpecification}

class MeterEntriesControllerSpec(implicit ee: ExecutionEnv)
  extends AuthSpec
    with ApiSpecification
    with MongoSpecification {

  import models.JsonFormats._

  "MeterEntriesController" should {

    "get entries by month" in new AuthContext with MongoContext {

      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )
      val meterId = "5b352e060d23cb43f5356301"

      new WithApplication(application) with MongoScope {
        val content: JsValue = contentAsJson(
          route(
            application,
            FakeRequest(GET, "/api/v1/entries?year=2018&month=6").withAuthenticator(loginInfo)
          ).get
        )

        content must beEqualTo(Json.toJson(
          Seq(
            MeterEntriesByMeterDto(
              meterId = "5b352e060d23cb43f5356301",
              meter = MeterDto(
                Some("5b352e060d23cb43f5356301"),
                "5b352d1821d900141fc5ab03",
                "Water cups",
                "Bars",
                "#238cd8",
                Some(UUID.fromString("a8a0c0ba-74aa-4bce-862d-08637ef7ba78")),
                None,
                None,
                None
              ),
              entries = Seq(
                MeterEntryDto(
                  Some("5b77038bb80100566b3883f2"),
                  "5b352e060d23cb43f5356301",
                  JsString("okayish"),
                  JsString("2018-06-18")
                )
              ),
              progress = None
            )
          )
        ))
      }
    }

    "get entries by week" in new AuthContext with MongoContext {

      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )

      new WithApplication(application) with MongoScope {

        val meterId = "5b352e060d23cb43f5356301"

        val content: JsValue = contentAsJson(
          route(
            application,
            FakeRequest(GET, "/api/v1/entries?year=2018&week=25").withAuthenticator(loginInfo)
          ).get
        )

        content must beEqualTo(Json.toJson(
          Seq(
            MeterEntriesByMeterDto(
              meterId = "5b352e060d23cb43f5356301",
              meter = MeterDto(
                Some("5b352e060d23cb43f5356301"),
                "5b352d1821d900141fc5ab03",
                "Water cups",
                "Bars",
                "#238cd8",
                Some(UUID.fromString("a8a0c0ba-74aa-4bce-862d-08637ef7ba78")),
                None,
                None,
                None
              ),
              entries = Seq(
                MeterEntryDto(
                  Some("5b77038bb80100566b3883f2"),
                  "5b352e060d23cb43f5356301",
                  JsString("okayish"),
                  JsString("2018-06-18")
                )
              ),
              progress = None
            )
          )
        ))
      }
    }

    "get entries by invalid week should return empty data" in new AuthContext with MongoContext {

      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )

      new WithApplication(application) with MongoScope {

        val meterId = "5b352e060d23cb43f5356301"

        route(
          application,
          FakeRequest(GET, "/api/v1/entries?year=2018&week=65").withAuthenticator(loginInfo)
        ) must beSome.which(status(_) == BAD_REQUEST)
      }
    }

    "delete entry by id" in new AuthContext with MongoContext {
      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )

      new WithApplication(application) with MongoScope {

        val meterId = "5b352e060d23cb43f5356301"

        route(
          application,
          FakeRequest(DELETE, "/api/v1/entries/5b77038bb80100566b3883f2").withAuthenticator(loginInfo)
        ) must beSome.which(status(_) == OK)
      }
    }

    "delete entry with invalid id should return BadRequest" in new AuthContext with MongoContext {
      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )

      new WithApplication(application) with MongoScope {
        route(
          application,
          FakeRequest(DELETE, "/api/v1/entries/nonono").withAuthenticator(loginInfo)
        ) must beSome.which(status(_) == BAD_REQUEST)
      }
    }

    "delete entry by id which does not exist should return NotFound" in new AuthContext with MongoContext {
      override val fixtures = Map(
        "meters" -> Seq("models/meters/meters.json"),
        "entries" -> Seq("models/entries/entries.json")
      )

      new WithApplication(application) with MongoScope {
        route(
          application,
          FakeRequest(DELETE, "/api/v1/entries/5b77038bb80100566b3883f1").withAuthenticator(loginInfo)
        ) must beSome.which(status(_) == NOT_FOUND)
      }
    }
  }

}
