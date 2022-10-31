package recursion

/**
 * Fix-point structure & catamorphism
 */

////////////////////////////////////////////////////////////////////////////////
trait Functor[F[_]] {
  def map[A,B](fa: F[A])(f: A => B): F[B]
}
////////////////////////////////////////////////////////////////////////////////
final case class Fix[F[_]](unfix: F[Fix[F]])    // <------- recursive structure
object Fix {
  def cata[F[_], A](f: F[A] => A)(fix: Fix[F])(implicit F: Functor[F]): A =
    f( F.map(fix.unfix)(cata[F,A](f)))          // <----- recursion
}
////////////////////////////////////////////////////////////////////////////////
sealed trait Expr[+A]

object Expr {
  case class Num(v: Int)          extends Expr[Nothing]
  case class Mul[A](x: A, y: A)   extends Expr[A]
  case class Div[A](x: A, y: A)   extends Expr[A]
  case class Plus[A](x: A, y: A)  extends Expr[A]
  case class Minus[A](x: A, y: A) extends Expr[A]

  implicit def ExprFunctor[T] = new Functor[Expr] {
    override def map[A, B](fa: Expr[A])(f: A => B): Expr[B] = fa match {
      case x @Num(_)   => x
      case Mul(x, y)   => Mul(f(x), f(y))
      case Div(x, y)   => Div(f(x), f(y))
      case Plus(x, y)  => Plus(f(x), f(y))
      case Minus(x, y) => Minus(f(x), f(y))
    }
  }
}

object ExprF {
  type ExprF = Fix[Expr]

  import Expr._
  def num(v: Int) = Fix[Expr](Num(v))
  def mul(x: Fix[Expr], y: Fix[Expr]) = Fix[Expr]( Mul(x, y))
  def div(x: Fix[Expr], y: Fix[Expr]) = Fix[Expr]( Div(x, y))
  def plus(x: Fix[Expr], y: Fix[Expr]) = Fix[Expr]( Plus(x, y))
  def minus(x: Fix[Expr], y: Fix[Expr]) = Fix[Expr]( Minus(x, y))


  def eval(e: Fix[Expr]): Int = Fix.cata[Expr,Int]{
    case Num(x)      => x
    case Mul(x, y)   => x * y
    case Div(x, y)   => x / y
    case Plus(x, y)  => x + y
    case Minus(x, y) => x - y
  }(e)

  def show(e: Fix[Expr]): String = Fix.cata[Expr,String]{
    case Num(x)      => s"$x"
    case Mul(x, y)   => s"($x * $y)"
    case Div(x, y)   => s"($x / $y)"
    case Plus(x, y)  => s"($x + $y)"
    case Minus(x, y) => s"($x - $y)"
  }(e)

}

object ExprFTest extends App {

  import ExprF._
  val es = mul(num(1), plus(num(2), num(3)))
  println( show (es) )
  println( eval (es) )

}
////////////////////////////////////////////////////////////////////////////////
sealed trait SList[+A,+S]
object SList {
  trait Nil extends SList[Nothing, Nothing]
  object Nil extends Nil
  case class Cons[A,+S](head: A, tail: S) extends SList[A, S]

  implicit def SListFunctor[T] = new Functor[SList[T, *]] {
    override def map[A, B](fa: SList[T, A])(f: A => B): SList[T, B] = fa match {
      case Nil => Nil
      case Cons(h,t) => Cons(h, f(t))   // <----
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
object FList {
  type FList[A] = Fix[SList[A, *]]

  import SList._
  def nil[A]  = Fix[SList[A, *]](Nil)
  def cons[A](x: A, xs: Fix[SList[A, *]]) = Fix[SList[A, *]](Cons(x, xs))

  def show[A](ls: Fix[SList[String, *]]): String =
    Fix.cata[SList[String, *], String]{
      case Nil => "Nil"
      case Cons(x, n) => s"$x::$n"
    }(ls)

}

object FListTest extends App {
  import FList._
  import SList._

  val xs: Fix[SList[String, *]] = cons("abc", cons("def", nil))

  val sum = Fix.cata[SList[String, *], Int]{
    case Nil => 0
    case Cons(x, n) => x.length + n
  }(xs)

  println( show(xs))
  println( sum)
}

////////////////////////////////////////////////////////////////////////////////
sealed trait Nat[+A] // Natural Number
object Nat {
  case object Zero extends Nat[Nothing]
  case class Succ[A](previous: A) extends Nat[A]

  implicit val NatFunctor = new Functor[Nat] {
    override def map[A, B](fa: Nat[A])(f: A => B): Nat[B] = fa match {
      case Zero => Zero
      case Succ(a) => Succ( f(a))
    }
  }
}

object NatTest extends App{
  import Nat._

  def zero: Fix[Nat] = Fix[Nat](Zero)
  def succ(p: Fix[Nat]): Fix[Nat] = Fix(Succ(p))
  def from(n: Int): Fix[Nat] =
    if( n <= 0) zero
    else succ(from(n-1))      // <<-- recursion, later anamorph


  def eval(n: Fix[Nat]): Int = Fix.cata[Nat, Int]{
    case Zero   => 0
    case Succ(a) => 1 + a
  }(n)

  def show(n: Fix[Nat]): String = Fix.cata[Nat, String]{
    case Zero   => s"0"
    case Succ(a) => s"succ($a)"
  }(n)

  val n = from(5) //succ(succ(succ(succ(succ(zero)))))

  println(show(n))
  println(eval(n))
}

