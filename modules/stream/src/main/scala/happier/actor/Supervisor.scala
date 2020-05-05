package happier
package actor

import akka.actor.Cancellable
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.annotation.DoNotInherit
import akka.stream.ActorMaterializer
import akka.stream.CompletionStrategy
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl._
import akka.util.ByteString
import akka.util.Timeout
import happier.api._
import happier.actor._
import happier.api.document.DocumentCategory
import java.util.concurrent.TimeoutException
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.existentials
import scala.language.higherKinds
import scala.util.{ Failure, Success, Try }
import scala.concurrent.Promise
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import akka.stream.Attributes
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.Done

object Supervisor {
  def apply() = active(Nil)

  private def active(waitingCaptcha: List[AskCaptcha]): Behavior[SupervisorCommand] = Behaviors
    .receive { (context, message) =>
      message match {
        case cmd: FindService =>
          val ref = cmd(context)
          Behaviors.same
        case message @ AskCaptcha(url, replyTo) =>
          System.err.println(s"Need to solve captcha : $url")
          active(message :: waitingCaptcha)
        case message @ CaptchaSolved(url, value) =>
          waitingCaptcha.indexWhere(_.url == url) match {
            case -1 =>
              active(waitingCaptcha)
            case idx =>
              waitingCaptcha(idx).replyTo ! message
              active(waitingCaptcha.patch(idx, Nil, 1))
          }
        case message: StartBehavior =>
          message(context)
          Behaviors.same
      }
    }
}
