
import scala.io._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.util._

import org.json4s._
import org.json4s.native.JsonMethods._

import scalaj.http._

object GitHubUserAuth {

  implicit val formats = DefaultFormats

  case class User(id:Long, username:String)

  lazy val token:Option[String] = sys.env.get("GHTOKEN") orElse {
    println("No token found: continuing without authentication")
    None
  }

  def fetchUserFromUrl(url:String):Future[User] = {
    val baseRequest = Http(url)
    val request = token match {
      case Some(t) => baseRequest.header("Authentication", s"token $token")
      case None => baseRequest
    }
    val response = Future { request.asString.body }
    val parsedResponse = response.map { r => parse(r) }
    parsedResponse.map { extractUser }
  }

  def extractUser(jsonResponse:JValue):User = {
    val o = jsonResponse.transformField {
      case ("login", name) => ("username", name)
    }
    o.extract[User]
  }

  def main(args:Array[String]) {
    val names = args.toList
    val users = for {
      name <- names
      url = s"https://api.github.com/users/$name"
      user = fetchUserFromUrl(url)
    } yield user

    names.zip(users).foreach { case(name, user) =>
      user.onComplete {
        case Success(u) => println(s" ** Extracted for $name: $u")
        case Failure(e) => println(s" ** Error fetching user $name: $e")
      }
    }

    Await.ready(Future.sequence(users), 1 minute)

  }

}
