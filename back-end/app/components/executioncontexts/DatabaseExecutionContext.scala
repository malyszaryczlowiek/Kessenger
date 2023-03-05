package components.executioncontexts

import akka.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext
import javax.inject.{Inject, Singleton}


// https://www.playframework.com/documentation/2.8.x/api/scala/play/api/libs/concurrent/CustomExecutionContext.html

@Singleton
class DatabaseExecutionContext @Inject()(system: ActorSystem)
  extends CustomExecutionContext(system, "database.dispatcher")
