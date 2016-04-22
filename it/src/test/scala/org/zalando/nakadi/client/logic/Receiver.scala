package org.zalando.nakadi.client.logic
//
//import java.net.InetSocketAddress
//import akka.actor.ActorSystem
//import akka.stream.scaladsl.GraphDSL.Implicits._
//import akka.stream.scaladsl._
//import akka.util.ByteString
//import scala.concurrent.{Future, Promise}
//import akka.event.Logging
//import org.zalando.nakadi.client.Connection
//import org.zalando.nakadi.client.logic.ReactiveStreamsSupport
//
//class Receiver(connection: Connection)(implicit val system: ActorSystem) extends ReactiveStreamsSupport {
//  def run(): Future[Unit] = {
//    val completionPromise = Promise[Unit]()
//
//    val serverConnection =  connection.connection()
//
//
//    val receiverFlow = GraphDSL.create() { implicit d =>
//      val in = Source. UndefinedSource[ByteString]
//      val out = UndefinedSink[ByteString]
//
//      val split = Broadcast[ByteString]
//
//      val mainFlow = Flow[ByteString]
//        .mapConcat(reconcileFrames.apply)
//        .map(MessageData.decodeFromString)
//        .map { md =>
//        logger.debug(s"Receiver: received msg: $md")
//        createFrame(md.id)
//      }
//
//      in ~> split
//
//      split ~> mainFlow ~> out
//      split ~> OnCompleteSink[ByteString] { t => completionPromise.complete(t); () }
//
//      (in, out)
//    }
//
//    val materializedMap = serverConnection.handleWith(receiverFlow)
//    val connectFuture = serverConnection.localAddress(materializedMap)
//
//    connectFuture.onSuccess { case _ => logger.debug(s"Receiver: connected to broker") }
//    connectFuture.onFailure { case e: Exception => logger.error("Receiver: failed to connect to broker", e) }
//
//    completionPromise.future
//  }
//}
//
//object SimpleReceiver extends App with SimpleServerSupport with Logging {
//  val receiver: Receiver = new Receiver(receiveServerAddress)
//  import receiver.system.dispatcher
//  receiver.run().onComplete(result => logger.info("Receiver: completed with result " + result))
//}