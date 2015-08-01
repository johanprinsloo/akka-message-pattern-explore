import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.example.actors.WorkCommandMessages.WorkSpec
import com.example.actors.WorkRunnerCmdMessages.StartWork
import com.example.actors._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class WorkRunnerTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
       with WordSpecLike with Matchers with BeforeAndAfterAll {

   def this() = this(ActorSystem("WorkDriverTest"))


   override def afterAll {
     TestKit.shutdownActorSystem(system)
   }


   import _system.dispatcher

   "An WorkRunner actor" must {
     "complete all the work phases in the absence of timeouts" in {
       val manager0 = system.actorOf(Props[WorkRunner],"manager0")
       manager0 ! StartWork(a = WorkSpec(500),
                            b = WorkSpec(300),
                            timeoutA = 1000 millis,
                            timeoutB = 1500 millis)

       val t0 = System.currentTimeMillis()
       fishForMessage(3000 milliseconds,"Complete both work phases in the specified time") {
         case WorkRunnerStatusMessages.DoneB( msg ) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis()-t0} milliseconds")
           true
         }
         case WorkRunnerStatusMessages.DoneA( msg ) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis()-t0} milliseconds")
           false
         }
         case WorkRunnerStatusMessages.CurrentState( st, msg ) => {
           info(s"Update from WorkRunner $msg in statet $st")
           info(s"measured as  ${System.currentTimeMillis()-t0} milliseconds")
           false
         }
         case _ => false
       }
     }

     "sense timeouts and abort work in the first phase" in {
       val manager1 = system.actorOf(Props[WorkRunner], "manager1")
       manager1 ! StartWork(  a = WorkSpec(800),
                              b = WorkSpec(300),
                              timeoutA = 500 millis,
                              timeoutB = 1500 millis)

       val t0 = System.currentTimeMillis()
       fishForMessage(1000 milliseconds, "Complete both work phases in the specified time") {
         case WorkRunnerStatusMessages.FatalError(st, msg) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           true
         }
         case WorkRunnerStatusMessages.DoneB(msg) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           false
         }
         case WorkRunnerStatusMessages.DoneA(msg) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           false
         }
         case WorkRunnerStatusMessages.CurrentState(st, msg) => {
           info(s"Update from WorkRunner $msg in statet $st")
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           false
         }
         case _ => false
       }
     }

     "sense timeouts and abort work in the second phase" in {
       val manager2 = system.actorOf(Props[WorkRunner], "manager2")
       manager2 ! StartWork(   a = WorkSpec(500),
                               b = WorkSpec(900),
                               timeoutA = 1000 millis,
                               timeoutB = 500 millis)

       val t0 = System.currentTimeMillis()
       fishForMessage(2000 milliseconds, "Complete both work phases in the specified time") {
         case WorkRunnerStatusMessages.FatalError(st, msg) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           true
         }
         case WorkRunnerStatusMessages.DoneB(msg) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           false
         }
         case WorkRunnerStatusMessages.DoneA(msg) => {
           info(msg)
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           false
         }
         case WorkRunnerStatusMessages.CurrentState(st, msg) => {
           info(s"Update from WorkRunner $msg in statet $st")
           info(s"measured as  ${System.currentTimeMillis() - t0} milliseconds")
           false
         }
         case _ => false
       }
     }
   }
 }
