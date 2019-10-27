package controllers.models

import com.mohiva.play.silhouette.test._
import models.JsonFormats._
import models.schema.Schema
import play.api.libs.json.JsValue
import play.api.test.{FakeRequest, WithApplication}
import spec.{AuthSpec, MongoSpecification}

class SchemaControllerSpec extends AuthSpec with MongoSpecification {

  "Get all schemas" in new AuthContext with MongoContext {

    override val fixtures = Map(
      "schemas" -> Seq("models/schemas/schemas.json")
    )

    new WithApplication(application) with MongoScope {
      val Some(result) = route(application, FakeRequest(GET, "/api/v1/schemas").withAuthenticator(loginInfo))
      val resultList: JsValue = contentAsJson(result)

      resultList.as[Seq[Schema]].length mustEqual 1
      status(result) must beEqualTo(OK)
    }
  }
}
