package lift


sealed trait Functor[F[_]] {
  def map[A,B](fa: F[A])(f: A => B): F[B]
}

object FunctorInstances {
  implicit val optionFunctor = new recursion.Functor[Option] {
    override def map[A, B](fa: Option[A])(f: A => B): Option[B] = fa map f
  }

  implicit val ListFunctor = new recursion.Functor[List] {
    override def map[A, B](fa: List[A])(f: A => B): List[B] = fa map f
  }
  // explicit compose
  def compose[F[_], G[_]](implicit F: recursion.Functor[F], G:recursion.Functor[G]) = new recursion.Functor[({type l[x] = F[G[x]]})#l] {
    override def map[A, B](fga: F[G[A]])(f: A => B): F[G[B]] = F.map(fga)(ga => G.map(ga)(f))
  }
}

///////////////////////////////////////////////////////////////////////////////////
object LiftMap {

  implicit class LiftMapOp[F[_],A](fa: F[A])(implicit F: recursion.Functor[F]) {
    def liftMap[B,C](f: B => C)(implicit lift: LiftFunctor[F[A],B,C]): lift.Out = lift(fa)(f)
  }

  trait LiftFunctor[FA, B, C] {
    type Out
    def apply(fa: FA)(f: B => C): Out
  }

  type Aux[FA,B,C,Out0] = LiftFunctor[FA,B,C] { type Out = Out0 }

  object LiftFunctor extends LowPriorityFunctor {
    implicit def base[F[_],A,B](implicit F: recursion.Functor[F])
    : Aux[F[A],A,B,F[B]] =
      new LiftFunctor[F[A],A,B] {
        override type Out = F[B]
        override def apply(fa: F[A])(f: A => B) = F.map(fa)(f)
      }
  }

  trait LowPriorityFunctor {
    implicit def recur[F[_],A,B,C](implicit F:recursion.Functor[F], lift: LiftFunctor[A,B,C])
    : Aux[F[A],B,C,F[lift.Out]] =
      new LiftFunctor[F[A],B,C] {
        override type Out = F[lift.Out]
        override def apply(fa: F[A])(f: B => C) = F.map(fa)(a => lift(a)(f))
      }
  }
}

///////////////////////////////////////////////////////////////////////////////////

object LiftMapTest extends App {
  import FunctorInstances._
  import LiftMap._

  val a = Option(1)
  val b = List(2)
  val c = Option(List(1))
  val d = c.liftMap( (i:Int) => i + 1)
  val e = c.liftMap( (l: List[Int]) => l.map(_.toString))
  println(e)
}

///////////////////////////////////////////////////////////////////////////////////
