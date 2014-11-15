package controllers

import play.api._
import play.api.mvc._
import models._

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

  // for javascript routes
  def javascriptRoutes = Action { implicit request =>
    import routes.javascript._
    Ok(Routes.javascriptRouter("jsRoutes")(
       routes.javascript.Websocket.stream,
       routes.javascript.Application.index
        )).as("text/javascript")
  }

}
