package components.executioncontexts

import com.google.inject.ImplementedBy

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[MyExecutionContextImpl])
trait MyExecutionContext extends ExecutionContext
