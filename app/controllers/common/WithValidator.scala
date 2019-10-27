package controllers.common

import com.eclipsesource.schema.drafts.Version7
import com.eclipsesource.schema.internal.validators.DefaultFormats
import com.eclipsesource.schema.urlhandlers.ClasspathUrlHandler
import com.eclipsesource.schema.{SchemaConfigOptions, SchemaFormat, SchemaValidator}
import play.api.libs.json.{JsResult, JsValue}

trait WithValidator {

  type ValidateFn = (=> JsValue) => JsResult[JsValue]

  val validator: SchemaValidator = SchemaValidator(Some(Version7(new SchemaConfigOptions {
    override def supportsExternalReferences: Boolean = true
    override def formats: Map[String, SchemaFormat] = DefaultFormats.formats
  }))).addUrlHandler(new ClasspathUrlHandler, ClasspathUrlHandler.Scheme)

}
