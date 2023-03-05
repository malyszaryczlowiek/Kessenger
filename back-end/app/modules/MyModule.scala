package modules

import com.google.inject.AbstractModule
import com.google.inject.name.Names

// A Module is needed to register bindings
class MyModule extends AbstractModule {
  override def configure() = {
    // Bind the `Hello` interface to the `EnglishHello` implementation as eager singleton.
    bind(classOf[FooTrait])
      .annotatedWith(Names.named("foo"))
      .to(classOf[FooClass])
      // .asEagerSingleton()

    bind(classOf[FooTrait])
      .annotatedWith(Names.named("bar"))
      .to(classOf[BarClass])
      // .asEagerSingleton()
  }
}
