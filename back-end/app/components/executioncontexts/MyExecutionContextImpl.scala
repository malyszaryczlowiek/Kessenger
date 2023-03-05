package components.executioncontexts

import akka.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton
class MyExecutionContextImpl @Inject() (system: ActorSystem)
  extends CustomExecutionContext(system, "my.executor") with MyExecutionContext {


}