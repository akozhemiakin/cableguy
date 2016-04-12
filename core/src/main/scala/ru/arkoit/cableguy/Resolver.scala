package ru.arkoit.cableguy

import ru.arkoit.cableguy.annotations._
import shapeless.LUBConstraint._
import shapeless._

case class Resolver[L <: HList : <<:[Provider]#λ](providers: L = HNil: HNil) {
  def resolve[T](implicit executor: ResolverExecutor[T, serviceNoTag, Resolver[L]], d1: DummyImplicit): T = executor.resolve(this)

  def resolveTagged[T, TG <: ServiceTag](implicit executor: ResolverExecutor[T, TG, Resolver[L]], d1: DummyImplicit): T = executor.resolve(this)
}
