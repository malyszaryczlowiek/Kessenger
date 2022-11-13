package modules

import play.api.{Configuration, Environment}
import play.api.inject._

class SecondModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): collection.Seq[Binding[_]] =
    Seq(
      bind[SomeTrait].qualifiedWith("foo2").to[FooClass],
      bind[SomeTrait].qualifiedWith("bar2").to[BarClass]
    )
}
