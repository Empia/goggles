package goggles.macros

import scalaz._, Scalaz._

case class ParseInfo[T](label: String, inType: T, outType: T, opticType: OpticType, compositeOpticType: OpticType) {

  def pretty: String = {
    val opticString =
      if (opticType == compositeOpticType) s"(${opticType.monoTypeName})"
      else s"(${opticType.monoTypeName}, returning ${compositeOpticType.monoTypeName})"

    def getTypeString(t: T) =
      if (t.toString.startsWith("=>")) s"($t)"
      else t.toString

    s"$label : ${getTypeString(inType)} => ${getTypeString(outType)} $opticString"
  }
}
case class ParseState[T,Arg](args: List[Arg], infos: List[ParseInfo[T]])

trait Parse[T, Arg, A] {
  self =>

  def apply(state: ParseState[T,Arg]): (Either[GogglesError,A], ParseState[T,Arg])

  final def map[B](f: A => B): Parse[T,Arg,B] = { args =>
    val (errorOrA, state) = self(args)
    (errorOrA.map(f), state)
  }

  final def flatMap[B](f: A => Parse[T,Arg,B]): Parse[T,Arg,B] = { args0 =>
    self(args0) match {
      case (Right(a), state) => f(a)(state)
      case (Left(err), state) => (Left(err), state)
    }
  }

  final def eval(args: List[Arg]): (Either[GogglesError,A], List[ParseInfo[T]]) = {
    val (errorOrA, ParseState(_, infos)) = apply(ParseState(args, Nil))
    (errorOrA, infos.reverse)
  }
}

object Parse {
  def pure[T,Arg,A](a: => A): Parse[T,Arg,A] = (Right(a), _)

  def fromOption[T, Arg, A](opt: Option[A], orElse: => GogglesError): Parse[T, Arg, A] = opt match {
    case Some(a) => pure(a)
    case None => raiseError(orElse)
  }

  def fromEither[T, Arg, A](either: Either[GogglesError,A]): Parse[T,Arg,A] =
    (either, _)

  def raiseError[T, Arg, A](e: GogglesError): Parse[T, Arg, A] =
    (Left(e), _)

  def getLastParseInfo[T,Arg]: Parse[T, Arg, Option[ParseInfo[T]]] = {
    case state @ ParseState(_, infos) => (Right(infos.headOption), state)
  }

  def getLastParseInfoOrElse[T,Arg](orElse: => GogglesError): Parse[T, Arg, ParseInfo[T]] = {
    case state @ ParseState(_, info :: _) => (Right(info), state)
    case state @ ParseState(_, Nil) => (Left(orElse), state)
  }

  def storeParseInfo[T,Arg](info: ParseInfo[T]): Parse[T, Arg, Unit] = {
    case ParseState(args, infos) => (Right(()), ParseState(args, info :: infos))
  }

  def popArg[T, Arg]: Parse[T, Arg, Arg] = {
    case state @ ParseState(arg :: rest, infos) => (Right(arg), ParseState(rest, infos))
    case state @ ParseState(Nil, _) => (Left(NotEnoughArguments), state)
  }

  implicit def monad[T,Arg] = new Monad[({type f[a]=Parse[T,Arg,a]})#f] {
    override def bind[A, B](fa: Parse[T,Arg,A])(f: A => Parse[T,Arg,B]): Parse[T,Arg,B] = fa.flatMap(f)
    override def point[A](a: => A): Parse[T,Arg,A] = pure(a)
  }
}