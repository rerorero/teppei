package models

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import play.api.Play
import play.api.mvc.MultipartFormData._
import play.api.libs.Files._

case class Image(filename: String)


object Images {

  val getImageDir = Play.current.configuration.getString("images.directory")

  implicit def imageToFileMapper: Image => java.io.File = { image =>
    new java.io.File(getImageDir.get, image.filename)
  }

  def mimeToExt(contentType:String) : Option[String] = contentType match {
    case "image/png" => Some("png")
    case "image/jpeg" => Some("jpg")
    case _ => None
  }

  def generateFileName(mime:String):Option[String] = {
    mimeToExt(mime) map { ext =>
      val formatter = new SimpleDateFormat("yyyyMMddHHmmss")
      val now = Calendar.getInstance.getTime
      "teppei_" + formatter.format(now) + "." + ext
    }
  }

  def add(file:FilePart[TemporaryFile]):Option[Image] = {
    for {
      dir <- getImageDir
      mime <- file.contentType
      filename <- generateFileName(mime)
    }yield {
      file.ref.moveTo(new File(dir, filename))
      Image(filename)
    }
  }

  def get(filename:String):Option[Image] = Some(Image(filename))

}
