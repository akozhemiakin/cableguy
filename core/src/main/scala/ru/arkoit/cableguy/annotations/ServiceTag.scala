package ru.arkoit.cableguy.annotations

import scala.annotation.StaticAnnotation

trait ServiceTag extends StaticAnnotation

final class serviceNoTag extends ServiceTag
