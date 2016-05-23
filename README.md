EXPERIMENTAL STUFF! WORK IN PROGRESS!
-------------------------------------

<p align="center"><img src="https://raw.githubusercontent.com/akozhemiakin/cableguy/master/cableguy-logo.jpeg" /></p>

Cableguy is yet another [Dependency Injection (DI)][di] library for Scala. It is compile time, macros based and runtime-reflection free.
Badges
------
[![Build Status](https://travis-ci.org/akozhemiakin/cableguy.svg?branch=master)](https://travis-ci.org/akozhemiakin/cableguy)
[![codecov.io](https://codecov.io/github/akozhemiakin/cableguy/coverage.svg?branch=master)](https://codecov.io/github/akozhemiakin/cableguy?branch=master)
[![Gitter](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/akozhemiakin/cableguy)

Quick facts
-----------
* It is a compile-time dependency injection implementation (I.e. no runtime-reflection involved)
* It is implemented with a help of Scala macros
* It allows you to perform deep Just-In-Time resolution
* It allows you to explicitly provide your dependencies using *providers*
* It allows you to use tagged dependencies
* It allows you to depend either on a singleton (every time the same instance) or a prototype (every time a new instance)
* It does not allow you (at least yet) to resolve not concrete types (traits, abstract classes) just-in-time. However you can do it with providers.

Installation
------------
This project is published at Maven Central. However, there is no release version yet. To depend on the *SNAPSHOT* version use the following sbt snippet:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "ru.arkoit" %% "cableguy-core" % "0.1.0-SNAPSHOT"
)
```
For now this project is compatible only with scala 2.11.\*. There is no final decision whether it's worth to support scala 2.10.\*. 

Just-In-Time resolution
-----------------------
```scala
import ru.arkoit.cableguy._

case class A(b: B)
case class B()

val a = Resolver().resolve[A] 
// eq: A(B())
```

Explicit providers
-------------------
To declare explicit provider you should extend *Provider* trait and use method definitions annotated with @provides annotation to provide some service. Then pass all of your providers to the Resolver constructor in a form of Shapeless HList (Do not worry if you do not know what is it, just import *shapeless._* and use the following pattern: ```Resolver(FooProvider :: BarProvider :: HNil)```). Obviously, explicit providers have higher priority than Just-In-Time resolution. Provider methods could have dependencies as well
```scala
import ru.arkoit.cableguy._
import ru.arkoit.cableguy.annotations._
import shapeless._

case class A(b: B, d: D)
case class B(label: String)
case class C()
case class D(num: Int, c: C)

object SomeProvider extends Provider {
  @provides
  def provideB = B("Foo")

  @provides
  def provideD(c: C) = D(10, c)
}

val a = Resolver(SomeProvider :: HNil).resolve[A]
// eq: A(B("Foo"), D(10, C()))
```

Tagged dependencies
--------------------------
```scala
import ru.arkoit.cableguy._
import ru.arkoit.cableguy.annotations._
import shapeless._

case class A(label: String)

class myTag extends ServiceTag

case class B(@myTag a: A)

object AProvider extends Provider {
  @provides
  def provideANotTagged = A("not tagged one")

  @provides @myTag
  def provideATagged = A("tagged one")
}

val result = Resolver(AProvider :: HNil).resolve[B]
// eq: B(A("tagged one"))
```

Scopes
------
Cableguy supports two scopes of dependency resolution: singleton and prototype. If the dependency is annotated with a *singletonScope* annotation (or is not scope-annotated at all) the same instance of the dependency will be used across all depended services (which depend on the singleton version of this service). If the dependency is annotated with a *prototypeScope* annotation, new instance of the dependency will be provided to each dependent service. The following example clearly demonstrates this behavior:
```scala
val r = scala.util.Random

class B {
  val rn = r.nextInt
}

class D {
  val rn = r.nextInt
}

class C(b: B, @prototypeScope d: D)
class A(c: C, b: B, @prototypeScope d: D)

val a = Resolver().resolve[A]

a.b.rn == a.c.b.rn // returns true
a.d.rn == a.c.d.rn // returns false
```

Other considerations
--------------------
Probably, the best way to use this library is to resolve single, top-level, "bootstrap" object to build single dependency tree. That way you'll end up with a minimum amount of macro-generated code and fast compilation time. 

Known issue with Scala Repl
---------------------------
In Scala Repl the Resolver could fail to resolve the requested service due to the issue with Scala Repl. To eliminate it try to perform the Resolver instantiation and the dependency resolution in two separate Scala Repl statement executions, like this:
```scala
....
val resolver = Resolver(FooProvider :: HNil)
```
hit *"Enter"*
```scala
val bar = resolver.resolve[Bar]
```
hit *"Enter"*

Status
------
Cableguy is a very young project and it is not tested in production yet (at least by its creator :)). Nevertheless
it has good test coverage and, because it performs dependency resolution compile-time, most of the possible issues should appear during the compilation phase.

Todos
-----
First of all, the existing code base (especially macros implementation) should be cleaned up and made less cryptic. Also, it would be useful to introduce some resolution tracing logic to inform the end user why something could not be resolved or why it is resolved that way and not another.

[di]: https://en.wikipedia.org/wiki/Dependency_injection
