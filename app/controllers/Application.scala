package controllers

import play.api.libs.json._
import play.api._
import play.api.mvc._
import models._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import models.Images.imageToFileMapper

object Application extends Controller {

  def index = Action {
    Ok(views.html.index(None))
  }

  def room(user:String) = Action { implicit request =>
    Ok(views.html.room(user, WSRoom.exists))
  }

  def clear = Action { implicit request =>
    WSRoom.clear
    Ok(views.html.index(Some("クリアしました。")))
  }

  def addImage = Action.async(parse.multipartFormData) {implicit request=>
    Future {
      request.body.file("image").flatMap { 
        Images.add(_)
      }.map{ img =>
        Logger.info("image added:"+img.filename)
        Ok(Json.obj("name" -> img.filename))
      }.getOrElse {
        Logger.info("Bad Request hx:")
        BadRequest
      }
    }
  }

  def getImage(filename:String) = Action {
    Images.get(filename).map { img => 
      Ok.sendFile(
        content = img,
        inline = true
        )
    }.getOrElse {
      Logger.info("Bad Request Image: "+filename)
      NotFound
    }
  }

  def listImages() = Action {
    Images.list().map { images =>
      Ok(views.html.images(images))
    }.getOrElse {
      Logger.info("failed to list images")
      NotFound
    }
  }

  def kick(user:String) = Action {
    Logger.info("try to kick "+user)
    WSRoom.kick(user)
    Ok(views.html.index(Some("kickしました。")))
  }

  // for javascript routes
  def javascriptRoutes = Action { implicit request =>
    import routes.javascript._
    Ok(Routes.javascriptRouter("jsRoutes")(
       routes.javascript.Websocket.stream,
       routes.javascript.Application.index,
       routes.javascript.Application.addImage,
       routes.javascript.Application.getImage,
       routes.javascript.Application.listImages
        )).as("text/javascript")
  }
}
