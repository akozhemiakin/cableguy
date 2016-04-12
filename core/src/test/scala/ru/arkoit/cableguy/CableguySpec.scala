package ru.arkoit.cableguy

import org.scalatest.{FlatSpec, Matchers, Tag}
import ru.arkoit.cableguy.annotations.{ServiceTag, prototypeScope, provides}
import shapeless.HNil

class CableguySpec extends FlatSpec with Matchers {
  "Resolver" should "be able to resolve service with no dependencies just in time" in {
    case class A()
    val result = Resolver().resolve[A]

    assert(result == A())
  }

  it should "resolve service with provider" in {
    case class A(label: String)

    object AProvider extends Provider {
      @provides
      def getA = A("Foo")
    }

    object OtherProvider extends Provider

    val result = Resolver(AProvider :: OtherProvider :: HNil).resolve[A]

    assert(result == A("Foo"))
  }

  it should "resolve complex structures using just-in-time and provided resolution" in {
    case class C(label: String)

    case class B(c: C)

    case class A(b: B)

    object CProvider extends Provider {
      @provides
      def provideС = C("foo")
    }

    val result = Resolver(CProvider :: HNil).resolve[A]

    assert(result == A(B(C("foo"))))
  }

  it should "resolve tagged service" in {
    case class A(label: String)

    class myTag extends ServiceTag

    object AProvider extends Provider {
      @provides
      def provideANotTagged1 = A("bad one")

      @provides
      def provideANotTagged2 = A("bad one")

      @provides @myTag
      def provideATagged = A("right one")

      @provides
      def provideANotTagged = A("bad one")
    }

    val result = Resolver(AProvider :: HNil).resolveTagged[A, myTag]

    assert(result == A("right one"))
  }

  it should "resolve service with tagged dependency" in {
    case class A(label: String)

    class myTag extends ServiceTag

    case class B(@myTag a: A)

    object AProvider extends Provider {
      @provides
      def provideANotTagged1 = A("bad one")

      @provides
      def provideANotTagged2 = A("bad one")

      @provides @myTag
      def provideATagged = A("right one")

      @provides
      def provideANotTagged = A("bad one")
    }

    val result = Resolver(AProvider :: HNil).resolve[B]

    assert(result == B(A("right one")))
  }

  it should "not type check on circular dependency" in {
    case class A(b: B)

    case class B(a: A)

    "val result = Resolver().resolve[A]" shouldNot typeCheck
  }

  it should "resolve service with provider with dependent services" in {
    class myTag extends ServiceTag

    case class B()

    case class C(label: String)

    case class AA(label: String, b : B, c: C)

    object AProvider extends Provider {
      @provides
      def getA(b: B, @myTag c: C) = AA("Foo", b, c)

      @provides @myTag
      def getC = C("foo")
    }

    val result = Resolver(AProvider :: HNil).resolve[AA]

    assert(result == AA("Foo", B(), C("foo")))
  }

  it should "resolve dependencies as a singltons by default" in {
    case class A(c: C, d: D)
    case class C(
      e: E
    )
    case class D(
      e: E
    )
    class E

    val result = Resolver().resolve[A]

    assert(result.c.e == result.d.e)
  }

  it should "correctly resolve prototype dependencies" in {
    case class A(c: C, d: D)
    case class C(
      @prototypeScope e: E
    )
    case class D(
      @prototypeScope e: E
    )
    class E

    val result = Resolver().resolve[A]

    assert(result.c.e !== result.d.e)
  }

  it should "fail resolving tagged service without provider" in {
    case class A(label: String)

    class myTag extends ServiceTag

    "val result = Resolver().resolveTagged[A, myTag]" shouldNot typeCheck
  }
}
