package csw.apps.clusterseed.admin.internal

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import csw.services.logging.internal.LoggingLevels.Level
import csw.services.logging.models.{FilterSet, LogMetadata}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val levelFormat: RootJsonFormat[Level] = new RootJsonFormat[Level] {
    override def write(obj: Level): JsValue = JsString(obj.name)

    override def read(json: JsValue): Level = json match {
      case JsString(value) ⇒ Level(value)
      case _               ⇒ throw new RuntimeException("can not parse")
    }
  }

  implicit val filterSetFormat: RootJsonFormat[FilterSet]     = jsonFormat1(FilterSet.apply)
  implicit val logMetadataFormat: RootJsonFormat[LogMetadata] = jsonFormat4(LogMetadata.apply)
}