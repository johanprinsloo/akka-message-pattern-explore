package com.example.actors

import akka.actor.{PoisonPill, Props, ActorRef, LoggingFSM}
import com.example.actors.WorkCommandMessages.WorkSpec
import com.example.actors.WorkRunnerCmdMessages.StartWork
import com.example.actors.WorkRunnerStatusMessages.{CurrentState, FatalError}

import scala.concurrent.duration._

/** FSM responsible for managing the work in multiple stages
  * here modeled as A and B
  * The goal is to test flexible work-timeout approaches
  *
  */
class WorkRunner extends LoggingFSM[State,Data] {
  import context._
  val checkRate = 10

  startWith(Init, Uninitialized)

  when(Init) {
    case Event(sw : StartWork,data) => {
      val worker = system.actorOf(Props[WorkDriver],s"workerFor${self.path.name}")
      worker ! sw.a
      val pulseAt = sw.timeoutA/checkRate
      system.scheduler.scheduleOnce( pulseAt, self, HeartBeatA(pulseAt))
      goto(StateA) using WorkData(sender(),worker,sw.timeoutA,sw.timeoutB,sw.a,sw.b,
                                  runningTime = 0 millis)
    }
  }

  when(StateA) {
    case Event( WorkStatusMessages.WorkDone( t ), d : WorkData) => {
      d.worker ! d.workPackageB
      d.client ! WorkRunnerStatusMessages.DoneA(s"Completed A in ${d.runningTime} ")
      val pulseAt = d.stateBTimeout/checkRate
      system.scheduler.scheduleOnce( pulseAt, self, HeartBeatB(pulseAt)) // noticed that the last HeartBeatA will be 'lost' - we don't care
      goto(StateB) using d.copy( runningTime = 0 millis)
    }
    case Event( WorkStatusMessages.WorkProgress( p ), d : WorkData) => {
      d.client ! CurrentState(StateA,s"Work progress report :$p %")
      stay()
    }
    case Event( HeartBeatA( pulse ), d : WorkData) => {
      val updateD = d.copy(runningTime = d.runningTime + pulse)
      if(updateD.runningTime > updateD.stateATimeout) {
        updateD.client ! FatalError(StateA,s"Timeout after ${updateD.runningTime}")
        self ! PoisonPill  // typically we want to client to decide this, not self destruct
        stay() using updateD
      } else {
        val pulseAt = updateD.stateATimeout/checkRate
        system.scheduler.scheduleOnce( pulseAt, self, HeartBeatA(pulseAt))
        updateD.worker ! WorkRunnerCmdMessages.ReportStatus()
        stay() using updateD
      }
    }
    case Event(WorkRunnerCmdMessages.ReportStatus, d : WorkData) => {
      sender() ! CurrentState(StateB,s"State B running time : ${d.runningTime}")
      stay()
    }
  }

  when(StateB) {
    case Event( WorkStatusMessages.WorkDone( t ), d : WorkData) => {
      d.client ! WorkRunnerStatusMessages.DoneB(s"Completed B in ${d.runningTime} ")
      goto(StateDone) using d.copy( runningTime = 0 millis)
    }
    case Event( WorkStatusMessages.WorkProgress( p ), d : WorkData) => {
      d.client ! CurrentState(StateB,s"Work progress report :$p %")
      stay()
    }
    case Event( HeartBeatA( pulse ), d : WorkData) => {
      val updateD = d.copy(runningTime = d.runningTime + pulse)
      if(updateD.runningTime > updateD.stateBTimeout) {
        updateD.client ! FatalError(StateB,s"Timeout after ${updateD.runningTime}")
        self ! PoisonPill  // typically we want to client to decide this, not self destruct
        stay() using updateD
      } else {
        val pulseAt = updateD.stateBTimeout/checkRate
        system.scheduler.scheduleOnce( pulseAt, self, HeartBeatA(pulseAt))
        updateD.worker ! WorkRunnerCmdMessages.ReportStatus()
        stay() using updateD
      }
    }
    case Event(WorkRunnerCmdMessages.ReportStatus, d : WorkData) => {
      sender() ! CurrentState(StateB,s"State B running time : ${d.runningTime}")
      stay()
    }
  }

  when(StateDone){
    case Event(WorkRunnerCmdMessages.ReportStatus, d : WorkData) => {
      sender() ! CurrentState(StateDone,s"All work complete")
      stay()
    }
  }
}

sealed trait State
case object Init extends State
case object StateA extends State
case object StateB extends State
case object StateDone extends State

sealed trait Data
case object Uninitialized extends Data
case class WorkData(client: ActorRef,
                    worker : ActorRef,
                    stateATimeout : FiniteDuration,
                    stateBTimeout : FiniteDuration,
                    workPackageA : WorkSpec,
                    workPackageB : WorkSpec,
                    runningTime : FiniteDuration ) extends Data

case class HeartBeatA( elapsed : FiniteDuration )
case class HeartBeatB( elapsed : FiniteDuration )



object WorkRunnerCmdMessages {
  case class StartWork( a : WorkSpec, b : WorkSpec, timeoutA : FiniteDuration, timeoutB : FiniteDuration )
  case class ReportStatus()
}

object WorkRunnerStatusMessages {
  case class FatalError(st : State, msg : String)
  case class CurrentState(st : State, msg : String)
  case class DoneA(msg : String)
  case class DoneB(msg : String)
}