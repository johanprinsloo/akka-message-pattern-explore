package com.example.actors

import akka.actor.{ActorRef, ActorLogging, Actor}
import scala.concurrent.duration._

/** Worker actor that simulates external API's
  *
  * e.g. Launch a VM or create a network
  * Here we assume that the worker can be queried for progress - this is not always so
  */
class WorkDriver extends Actor with ActorLogging {
  import context._

  var updates = 5.0
  var elapsedTime : Int = 0

  def receive = {
    case WorkCommandMessages.WorkSpec(time, upd) => {
      elapsedTime = 0
      updates = upd
      val workTick = (time/updates).toInt
      system.scheduler.scheduleOnce( workTick milliseconds, self, WorkSimulator(workTick))
      become( working( time,sender() ) )
    }
  }

  def working( workTime : Int, client : ActorRef ) : Receive = {
    case c : WorkCommandMessages.ReportProgress => {
      sender ! WorkStatusMessages.WorkProgress(100.0 * elapsedTime / workTime.toDouble)
    }
    case WorkSimulator( delta ) => {
      elapsedTime = elapsedTime + delta
      if(elapsedTime >= workTime) {
        client ! WorkStatusMessages.WorkDone(elapsedTime)
        unbecome()
      }
      else {
        val workTick = (workTime/updates).toInt
        system.scheduler.scheduleOnce( workTick milliseconds, self, WorkSimulator(workTick))
      }
    }
  }
}

case class WorkSimulator(deltaT : Int)

object WorkCommandMessages {
  case class WorkSpec( workDuration : Int, updates : Double = 5.0 )
  case class ReportProgress()
}

object WorkStatusMessages {
  case class WorkProgress( percentage : Double )
  case class WorkDone( elapsed : Int )
}
