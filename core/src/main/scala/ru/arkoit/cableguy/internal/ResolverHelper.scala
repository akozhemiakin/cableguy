package ru.arkoit.cableguy.internal

import scala.annotation.tailrec
import scala.collection.immutable.{:: => Cons}
import scala.reflect.macros.blackbox

import shapeless._
import ru.arkoit.cableguy._
import ru.arkoit.cableguy.annotations._

private[cableguy] class ResolverHelper(val c: blackbox.Context) {
  import c.universe._

  private[this] val singletonScopeTpe = weakTypeOf[singletonScope]
  private[this] val prototypeScopeTpe = weakTypeOf[prototypeScope]
  private[this] val noTagTpe = weakTypeOf[serviceNoTag]

  private[this] case class ProviderRef(
    tpe: Type,
    term: TermName,
    expr: Tree
  )

  private[this] case class Container(
    items: List[ContainerItem],
    providers: List[ProviderRef],
    index: Int = 1
  )

  private[this] case class ContainerItem(
    termName: TermName,
    expr: Tree,
    tpe: Type,
    scope: Type,
    tag: Type
  )

  private[this] case class ResolutionTask(
    tpe: Type,
    scope: Type,
    tag: Type,
    container: Container,
    precedingTasks: Vector[ResolutionTask] = Vector()
  )

  private[this] case class ResolutionResult(
    task: ResolutionTask,
    container: Container,
    item: ContainerItem
  )

  private[this] trait ResolverAgent {
    def resolve(resolutionTask: ResolutionTask): Option[ResolutionResult]
  }

  private[this] object ProvidersResolverAgent extends ResolverAgent {
    override def resolve(resolutionTask: ResolutionTask): Option[ResolutionResult] = {
      val container = resolutionTask.container

      def resolveMethod(p: ProviderRef, s: MethodSymbol): Option[ResolutionResult] = s.paramLists match {
        case x @ (Nil | Cons(Nil, Nil)) =>
          val item = ContainerItem(
            TermName(s"provided_${resolutionTask.tpe.typeSymbol.name}_${container.index}"),
            q"${p.term}.${TermName(s.name.toString)}",
            resolutionTask.tpe,
            resolutionTask.scope,
            resolutionTask.tag
          )
          Some(ResolutionResult(
            resolutionTask,
            container copy (items = container.items :+ item, index = container.index + 1),
            item
          ))
        case Cons(h, Nil) =>
          val tasks = h.map(x => ResolutionTask(
            x.typeSignature,
            getSymbolScope(x),
            getSymbolTag(x),
            resolutionTask.container,
            resolutionTask.precedingTasks :+ resolutionTask
          ))
          resolveAllOrNone(tasks) match {
            case Some(x) =>
              val tpe = resolutionTask.tpe
              val cont = x.last.container
              val item = ContainerItem(
                TermName(s"provided_${resolutionTask.tpe.typeSymbol.name}_${cont.index}"),
                q"${p.term}.${TermName(s.name.toString)}(..${x.map(_.item.termName)})",
                tpe,
                resolutionTask.scope,
                resolutionTask.tag
              )

              Some(ResolutionResult(
                resolutionTask,
                cont.copy(
                  items = cont.items :+ item, index = cont.index + 1
                ),
                item
              ))
            case None => None
          }
        case _ => None
      }

      def scanProvider(p: ProviderRef): Option[ResolutionResult] = p.tpe.members
        .filter(x => x.isTerm &&
          x.isPublic &&
          x.isMethod &&
          x.annotations.exists(j => j.tree.tpe =:= weakTypeOf[provides]) &&
          (
            resolutionTask.tag =:= weakTypeOf[serviceNoTag] ||
            x.annotations.exists(j => j.tree.tpe =:= resolutionTask.tag)
          ) &&
          x.typeSignature.resultType <:< resolutionTask.tpe
        ).foldLeft(None: Option[ResolutionResult])((a, b) => a match {
          case x @ Some(_) => x
          case None => resolveMethod(p, b.asMethod)
        })

      resolutionTask.container.providers.foldLeft(None: Option[ResolutionResult])((a, b) => a match {
        case x @ Some(_) => x
        case None => scanProvider(b)
      })
    }
  }

  private[this] object JustInTimeResolverAgent extends ResolverAgent {
    override def resolve(resolutionTask: ResolutionTask): Option[ResolutionResult] = {
      // JustInTime resolver agent can not resolve tagged service (at least for now)
      if (!(resolutionTask.tag =:= noTagTpe)) {
        return None
      }

      // Only concrete classes
      val classSymbol = resolutionTask.tpe.typeSymbol.asClass
      if (classSymbol.isAbstract || classSymbol.isTrait) {
        return None
      }

      def extractApplicableConstructor(tpe: Type): Option[Symbol] = tpe.members
         .find(x => x.isPublic && x.isMethod &&
                    x.asMethod.isPrimaryConstructor &&
                    x.name.toString != "$init$" &&
                    x.asMethod.paramLists.length <= 1)
         .flatMap(x => Some(x))

      def extractConstructorParams(s: Symbol): List[Symbol] = s.asMethod.paramLists match {
        case l if l.isEmpty => List()
        case l => l.head
      }

      for (
        tpe <- Some(resolutionTask.tpe);
        constructor <- extractApplicableConstructor(tpe);
        args <- Some(extractConstructorParams(constructor));
        resolvedArgs <- resolveAllOrNone(args.map(x => ResolutionTask(
          x.typeSignature,
          getSymbolScope(x),
          getSymbolTag(x),
          resolutionTask.container,
          resolutionTask.precedingTasks :+ resolutionTask
        )))
      ) yield {
        val container = resolvedArgs match {
          case k if k.isEmpty => resolutionTask.container
          case k => k.last.container
        }

        val item = ContainerItem(
          TermName(s"jit_${tpe}_${resolutionTask.container.index}"),
          q"new ${tpe.typeConstructor.typeSymbol}(..${resolvedArgs.map(_.item.termName)})",
          tpe,
          resolutionTask.scope,
          resolutionTask.tag
        )

        ResolutionResult(
          resolutionTask,
          container.copy(
            items = container.items :+ item, index = container.index + 1
          ),
          item
        )
      }
    }
  }

  // Order matters!
  private[this] val resolverAgents: List[ResolverAgent] = List(
    ProvidersResolverAgent,
    JustInTimeResolverAgent
  )

  def resolve[T : WeakTypeTag, TG <: ServiceTag : WeakTypeTag, R <: Resolver[_] : WeakTypeTag]: Tree = {
    val tpe = weakTypeOf[T]
    val resolverTpe = weakTypeOf[R]
    // Initialize container
    val container = Container(Nil, extractProviderRefs(resolverTpe))

    resolveTask(ResolutionTask(tpe, prototypeScopeTpe, weakTypeOf[TG], container)) match {
      case Some(x) => resolutionResultToTree(x, resolverTpe)
      case None => c.abort(c.enclosingPosition, s"Cableguy is not able to resolve the requested service (type: ${tpe.typeSymbol.name})")
    }
  }

  private[this] def resolveTask(task: ResolutionTask): Option[ResolutionResult] = {
    // Check for circular reference
    if (task.precedingTasks.exists(x =>
      x.tpe == task.tpe &&
      x.tag == task.tag
    )) {
      return None
    }

    // Search in already resolved (cached) services if the scope of this task is singleton
    val cached = if (task.scope =:= singletonScopeTpe) {
      task.container.items.find(x =>
        x.scope =:= singletonScopeTpe &&
        x.tag =:= task.tag &&
        x.tpe <:< task.tpe
      ) match {
        case Some(i) => Some(ResolutionResult(task, task.container, i))
        case _ => None
      }
    }
    else {
      None
    }

    cached match {
      case x @ Some(_) => x
      case None => resolverAgents.foldLeft(None: Option[ResolutionResult])((a, b) => a match {
        case x @ Some(_) => x
        case _ => b.resolve(task)
      })
    }
  }

  private[this] def extractProviderRefs(resolverTpe: Type): List[ProviderRef] = {
    @tailrec
    def map(provs: Type, t: List[ProviderRef], i: Int): List[ProviderRef] = provs match {
      case x if x <:< typeOf[::[_, _]] =>
        map(x.typeArgs(1), t :+ ProviderRef(x.typeArgs.head, TermName(s"provider$i"), q"providers.at($i)"), i + 1)
      case x if x <:< typeOf[HNil] => t
      case _ => c.abort(c.enclosingPosition, "Cableguy providers lookup failure")
    }

    map(resolverTpe.typeArgs.head, Nil, 0)
  }

  private[this] def resolutionResultToTree[R](resolutionResult: ResolutionResult, resolverTpe: Type): Tree = {
    val providerDefinitions = resolutionResult.container.providers.map(x => q"lazy val ${x.term} = ${x.expr}")
    val serviceDefinitions = resolutionResult.container.items.map(x => q"lazy val ${x.termName} = ${x.expr}")
    val item = resolutionResult.item

    val code = q"""
        import shapeless._
        import ru.arkoit.cableguy._

        new ResolverExecutor[${item.tpe}, ${item.tag}, $resolverTpe] {
          def resolve(resolver: $resolverTpe): ${item.tpe} = {

            lazy val providers = resolver.providers

            ..$providerDefinitions

            ..$serviceDefinitions

            ${resolutionResult.item.termName}
          }
        }
      """
    code
  }

  /**
    * Tries to resolve all tasks sequentially, fails fast and returns None if it can not resolve some task.
    *
    * Container is taken from the first task and then modified on each resolution iteration
    */
  private[this] def resolveAllOrNone(tasks: List[ResolutionTask]) =
    tasks.foldLeft(Some(Vector()) : Option[Vector[ResolutionResult]])((a, b) => a match {
      case Some(l) =>
        val cnt = l.lastOption match {
          case None => b.container
          case Some(x) => x.container
        }
        resolveTask(b.copy(container = cnt)) match {
          case Some(x) => Some(l :+ x)
          case None => None
        }
      case None => None
    })

  private[this] def getSymbolScope(s: Symbol): Type =
    s.annotations.find(_.tree.tpe <:< weakTypeOf[ServiceScope]) match {
      case Some(x) => x.tree.tpe
      case _ => singletonScopeTpe
    }

  private[this] def getSymbolTag(s: Symbol): Type =
    s.annotations.find(_.tree.tpe <:< weakTypeOf[ServiceTag]) match {
      case Some(x) => x.tree.tpe
      case _ => noTagTpe
    }
}
