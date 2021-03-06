package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.SupervisorStrategy._
import akka.actor._
import org.apache.zookeeper.KeeperException
import tanukkii.reactivezk._
import tanukkii.reactivezk.ZooKeeperSession.Close
import org.apache.zookeeper.Watcher.Event.KeeperState._
import scala.util.Random

class WorkerSupervisor extends Actor with ActorLogging {
  import WorkerProtocol._

  context.system.eventStream.subscribe(self, classOf[ZooKeeperWatchEvent])

  val settings = ZKSessionSettings(context.system)

  val zookeeperSession = context.actorOf(ZooKeeperSessionActor.props(settings.connectString, settings.sessionTimeout), "zookeeper-session")

  val worker = context.actorOf(Worker.props(Integer.toHexString(new Random().nextInt()), zookeeperSession, self), "worker")

  override def supervisorStrategy: SupervisorStrategy = {
    val keeperDecider: Decider = {
      case e: KeeperException => {
        log.error(e, "ZooKeeper error")
        zookeeperSession ! Close
        Stop
      }
    }
    OneForOneStrategy()(keeperDecider orElse SupervisorStrategy.defaultDecider)
  }

  def receive: Receive = {
    case ZooKeeperWatchEvent(e) if e.getState == SyncConnected => {
      context.system.eventStream.unsubscribe(self, classOf[ZooKeeperWatchEvent])
      worker ! CreateAssignNode
    }
    case WorkerBootstrapFinished => worker ! Register
    case Registered => worker ! GetTasks
  }
}

object WorkerSupervisor {
  def props = Props(new WorkerSupervisor)
}