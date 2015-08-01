import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import com.example.actors.{WorkStatusMessages, WorkCommandMessages, WorkDriver}
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll, FlatSpec}
import org.scalatest.matchers.ShouldMatchers
import scala.concurrent.duration._

class WorkDriverTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
      with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("WorkDriverTest"))


  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val worker = system.actorOf(Props[WorkDriver],"worker")
  import _system.dispatcher

  "An WorkDriver actor" must {
    "complete its simulated work in roughly the specified time" in {
      worker ! WorkCommandMessages.WorkSpec(1000)
      system.scheduler.scheduleOnce(500 millis, worker, WorkCommandMessages.ReportProgress())

      worker !  WorkCommandMessages.ReportProgress()

      val t0 = System.currentTimeMillis()
      fishForMessage(1400 milliseconds,"Complete work in the specified time") {
        case WorkStatusMessages.WorkDone( elapsed ) => {
          info(s"completed work in a reported $elapsed milliseconds")
          val t1 = System.currentTimeMillis()
          info(s"measured as  ${t1-t0} milliseconds")
          true
        }
        case WorkStatusMessages.WorkProgress(progress) => {
          val t1 = System.currentTimeMillis()
          info(s"status update : $progress % at a measured ${t1-t0} milliseconds")
          false
        }
        case _ => false
      }
    }

    "reset and work again when complete" in {
      val t0 = System.currentTimeMillis()
      worker ! WorkCommandMessages.WorkSpec(500)
      system.scheduler.scheduleOnce(100 millis, worker, WorkCommandMessages.ReportProgress())
      system.scheduler.scheduleOnce(200 millis, worker, WorkCommandMessages.ReportProgress())
      system.scheduler.scheduleOnce(300 millis, worker, WorkCommandMessages.ReportProgress())
      system.scheduler.scheduleOnce(400 millis, worker, WorkCommandMessages.ReportProgress())

      fishForMessage(1000 milliseconds,"Complete work in the specified time") {
        case WorkStatusMessages.WorkDone( elapsed ) => {
          info(s"completed work in a reported $elapsed milliseconds")
          val t1 = System.currentTimeMillis()
          info(s"measured as  ${t1-t0} milliseconds")
          true
        }
        case WorkStatusMessages.WorkProgress(progress) => {
          val t1 = System.currentTimeMillis()
          info(s"status update : $progress % at a measured ${t1-t0} milliseconds")
          false
        }
        case _ => false
      }
    }

  }
}
