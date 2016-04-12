package ru.arkoit.cableguy

import ru.arkoit.cableguy.annotations.provides
import ru.arkoit.cableguy.nested.Bar

object BarProvider extends Provider {
  @provides
  def provideBar = Bar()
}
