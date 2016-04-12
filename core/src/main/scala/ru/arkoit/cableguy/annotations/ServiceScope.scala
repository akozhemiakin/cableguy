package ru.arkoit.cableguy.annotations

import scala.annotation.StaticAnnotation

sealed trait ServiceScope extends StaticAnnotation

final class singletonScope extends ServiceScope

final class prototypeScope extends ServiceScope
