package models

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent._
import scala.language.postfixOps
import play.api._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Concurrent.Channel
import scala.concurrent.Await
import java.util.Calendar
import akka.actor.Scheduler

/**
 * genarator of replay JSON
 */ 
object ClientMessage {
  def error(msg:String) = Json.obj(
      "kind" -> "error",
      "msg" -> msg
      )
  
  def commitpic(from:String) = Json.obj(
      "kind" -> "commitPic",
      "from" -> from
      )

  // def picAdded(picture:Picture) = Json.obj(
  //     "kind" -> "picAdded",
  //     "pic" -> picture.toJson,
  //     "picnum" -> pictures.getCountByUser(picture.userid)
  //     )
  //
  // def picRemoved(picture:Picture) = Json.obj(
  //     "kind" -> "picRemoved",
  //     "pic" -> picture.toJson,
  //     "picnum" -> pictures.getCountByUser(picture.userid)
  //     )
      
  def joined(user:String) = Json.obj(
      "kind" -> "joined",
      "user" -> user
      )
  
  def left(user:String) = Json.obj(
      "kind" -> "left",
      "user" -> user
      )

  def kick(user:String) = Json.obj(
    "kind" -> "kick",
    "user" -> user
    )

  
  // def captureData(from:User, data:String) = Json.obj(
  //     "kind" -> "capture",
  //     "user" -> from.id.toString,
  //     "data" -> data
  //     )
      
  def captureEcho() = Json.obj(
      "kind" -> "capEcho"
      )
}

// Room controller interface 
object WSRoom {
  implicit val timeout = Timeout(1 second) 
  lazy val mediator = Akka.system.actorOf(Props[WSRoom])
  // starts clean job periodically
  val normalizeJob = Akka.system.scheduler.schedule(300 minutes, 300 minutes, mediator, Clean)

  // error streaming
  def errorIteratee(error:String) : (Iteratee[JsValue,_],Enumerator[JsValue]) = {
    // Connection error. finished Iteratee sending EOF
    val iteratee = Done[JsValue,Unit]((),Input.EOF)
    // Send an error and close the socket
    val enumerator =  Enumerator[JsValue](ClientMessage.error(error)).andThen(Enumerator.enumInput(Input.EOF))
    (iteratee,enumerator)        
  }
  
  // open sockets
  def join(user:String): Future[(Iteratee[JsValue,_],Enumerator[JsValue])] = {
    (mediator ? Join(user)).map {
      case Connected(session) => 
        Logger.debug(s"websocket opend : [$user]")
        
        val iteratee = Iteratee.foreach[JsValue] { jsval =>
          /**
           * incoming JSON
           */
          (jsval \ "kind").asOpt[String] match{
            // // stream video data
            // case Some("capture") => for{
            //     data <- (jsval \ "data").asOpt[String]
            //     needEcho <- (jsval \ "echo").asOpt[Boolean]
            //   }yield mediator ! Capture(userid, data, needEcho)
            // take photo 
            case Some("commitPic") => (jsval \ "from").asOpt[String].map( from => mediator ! CommitPicture(from) )
            //
            case _ => Logger.error("unknown kind"+jsval)
          }
          
        }.map { _ =>
          Logger.debug(s" --- websocket closed --- : [$user]")
          mediator ! Leave(user)
        }
        
        (iteratee, session.enumlator)
        
      case MediatorFailed(error) => errorIteratee(error)      
    }
  } 

  def clear = {
    mediator ? Clean
  }

  def kick(user:String) = {
    mediator ? Kick(user)
  }

  def exists = {
    val f = (mediator ? Exists).map {
      case ExistsResult(user) => user
      case _ => Some("")
    }
    Await.result(f, Duration.Inf).getOrElse("")
  }
  
  // def pictureAdded(pic:Picture) = {
  //   mediator ? PictureAdded(pic)
  // }
  //
  // def pictureRemoved(pic:Picture) = {
  //   mediator ? PictureRemoved(pic)
  // }
}

/**
 * websocket enumlator wrppaer
 */ 
class UserSession(val mediator:ActorRef, val username:String) {
  self=>
  val enumlator = Concurrent.unicast[JsValue](onStart, onComplete, onError)
  var channel: Option[Channel[JsValue]] = None
  
  def onStart: Channel[JsValue] => Unit = {
    ch =>
      channel = Some(ch)
      mediator ! Joined(self)
  }
  
  def onError: (String, Input[JsValue]) => Unit = {
    (message, input) =>
      Logger.error("Failed in UserSession generation " + message)
  }
  
  def onComplete = Logger.info(s"User $username session destroyed")
  
  def close = channel.map(_.eofAndEnd)
}

/**
 * actor of room controller 
 */
class WSRoom extends Actor {  
  var sessions = scala.collection.mutable.ListBuffer.empty[UserSession]

  /**
   * broadcast the room of user specified
   */
  def broadcast(sender:String, includeSelf:Boolean, sendee:(Channel[JsValue])=>Unit ) = {
    val roomers = if (includeSelf) {
      sessions
    }else{
      sessions.filter(_.username != sender)
    }
    roomers.foreach(_.channel.map(sendee(_)))
  }
  
  /**
   * unicast the user specified
   */
  def unicast(target:String, sendee:(Channel[JsValue])=>Unit ) = sessions.find(_.username == target).map{ targetSession =>
    targetSession.channel.map(sendee(_))
  }
  
  def removeUserSession(user:String) = {
    sessions.find(_.username == user).map(_.close)
    sessions = sessions.filterNot(_.username == user)
  }
  
  def addSession(user:String) = {
    val newsession = new UserSession(self,  user)
    sessions += newsession
    // modify DB
    newsession
  }
  
  def removeAll = {
    Logger.info("room has been dead : ")
    sessions.map(_.close)
    sessions.clear 
  }
  
  /**
   * receiver definitions
   */
  def receive = {
    
    // first, make user's session
    case Join(user:String) => {
      removeUserSession(user)
      val newsession = addSession(user)
      sender ! Connected(newsession)
    }
    
    // users going to leave from the room
    case Leave(user:String)  => {
      Logger.info("leave:"+user)
      val message = ClientMessage.left(user)
      broadcast(user, false, _.push(message))
      removeUserSession(user)
    }

    // notify room that user has joined
    case Joined(session) => {
      Logger.info("joined:"+session.username)
      val message = ClientMessage.joined(session.username)
      broadcast(session.username, false, _.push(message))
    }
    
    // request to take picture 
    case CommitPicture(from) => {
      Logger.info("commit picture from:"+from)
      val message = ClientMessage.commitpic(from)
      broadcast(from, false, _.push(message))
    }

    // // picture saving completed
    // case PictureAdded(pic) => {
    //   val message = ClientMessage.picAdded(pic)
    //   broadcast(pic.userid, true, _.push(message))
    // }
    //
    // // picture removing completed
    // case PictureRemoved(pic) => {
    //   val message = ClientMessage.picRemoved(pic)
    //   broadcast(pic.userid, true, _.push(message))
    // }
    //
    // // stream of capture data 
    // case Capture(uid, data, needEcho) => {
    //   Logger.debug(s"websocket captured : user=$uid datanum=" + data.length().toString)
    //   users.findById(uid).map( user=> {
    //     val message = ClientMessage.captureData(user, data)
    //     broadcast(user.id.get, false, _.push(message))
    //     if (needEcho) unicast(uid, _.push(ClientMessage.captureEcho))
    //   })
    // }
    
    case Clean => {
      Logger.info("CLEAN runs")
      // removeAll
    }

    case Exists => {
      Logger.info("exists")
      sender ! ExistsResult(sessions.headOption.map{_.username})
    }

    case Kick(user) => {
      val message = ClientMessage.kick(user)
      broadcast(user, true, _.push(message))
    }
  }
}

// message
case class Join(user: String)
// case class DestroyRoom(roomid: Long)
case class Leave(user: String)
// case class PictureAdded(picture:Picture)
// case class PictureRemoved(picture:Picture)
case class Exists()
case class Kick(user:String)

// response
case class MediatorFailed(msg: String)
case class Connected(session:UserSession)
case class Joined(session:UserSession)
case class ExistsResult(user:Option[String])
case class CommitPicture(from:String)
// case class Capture(uid:Long, data:String, needEcho:Boolean)
case class Clean()

