
package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.JsValue
import scala.concurrent._
import play.api.libs.iteratee._
import models._


object Websocket extends Controller {
  
  def stream(user: String) = WebSocket.async[JsValue] { request =>
    Logger.info(s"ws:" + request)
    WSRoom.join(user)
  }
}

