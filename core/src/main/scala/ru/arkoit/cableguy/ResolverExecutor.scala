package ru.arkoit.cableguy

import scala.language.experimental.macros

import ru.arkoit.cableguy.annotations.ServiceTag
import ru.arkoit.cableguy.internal.ResolverHelper

trait ResolverExecutor[T, TG <: ServiceTag, R <: Resolver[_]] {
  def resolve(resolver: R): T
}

object ResolverExecutor {
  implicit def materializeResolverExecutor[T, TG <: ServiceTag, R <: Resolver[_]]: ResolverExecutor[T, TG, R] = macro ResolverHelper.resolve[T, TG, R]
}
