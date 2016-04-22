package org.zalando.nakadi.client.done

//class EventPublishingActor extends Actor with ActorLogging with ActorPublisher[Url] {
//
// val queue: mutable.Queue[Url] = mutable.Queue()
// val visited: mutable.Set[String] = mutable.Set()
//
// override def receive: Receive = {
//   case url: Url =>
//     if (!visited(url.url)) {
//       visited += url.url
//       queue.enqueue(url)
//       if (isActive && totalDemand > 0) {
//         onNext(queue.dequeue())
//       }
//     }
// }
//}