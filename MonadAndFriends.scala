import Monad._

////////////////////////////////////////////////////////////////////////////////
trait Monoid[A] {
  def zero : A
  def op (x: A, y: A): A
}

object Monoid {

  def dual[A]( m: Monoid[A]): Monoid[A] = new Monoid[A] {
    override def zero: A = m.zero
    override def op(x: A, y: A): A = m.op(y, x)
  }

  def product[A,B](ma: Monoid[A], mb: Monoid[B]): Monoid[(A, B)] = new Monoid[(A,B)] {
    override def zero: (A, B) = (ma.zero, mb.zero)
    override def op(x: (A, B), y: (A, B)): (A, B) = (ma.op(x._1, y._1), mb.op(x._2, y._2))
  }

  def endo[A]: Monoid[A => A] = new Monoid[ A => A] {
    override def zero: A => A = identity
    override def op(x: A => A, y: A => A): A => A = x andThen( y)
  }
}

////////////////////////////////////////////////////////////////////////////////
trait Foldable[F[_]] {

  def foldMap[A,B](as: F[A], m: Monoid[B])(f: A => B): B =
    foldRight(as)(m.zero)((a, b) => m.op(f(a), b))

  import Monoid.{dual, endo}
  def foldRight[A,B](as: F[A])(z: B)(f: (A,B) => B ): B =
    foldMap(as, endo[B])( f.curried)(z)

  def foldLeft[A,B](as: F[A])(z: B)(f: (B, A) => B ): B =
    foldMap(as, dual(endo[B]))( a => b => f(b, a))(z)

  def concatenate[A](as: F[A], m: Monoid[A]) : A =
    foldMap(as, m)(identity)

  def toList[A](as: F[A]): List[A] = {
    foldRight(as)(List.empty[A])( _ :: _)
  }
}

object ExerciseMonoidFold {
  ////////////////////////////////////////////////////////////////////////////////
  def foldMapV[A,B](as: IndexedSeq[A], m: Monoid[B])(f: A => B): B = as.length match {
    case 0 => m.zero
    case 1 => f(as.head)
    case n => val (l, r) = as.splitAt(n/2)
      m.op(
        foldMapV(l, m)(f),
        foldMapV(r, m)(f)
      )
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  def readerMonoid[R,A](m: Monoid[A]): Monoid[R => A] = new Monoid[R => A] {
    override def zero: R => A = r => m.zero
    override def op(x: R => A, y: R => A): R => A = r => m.op( x(r), y(r))
  }

  // not efficient..
  def merge[K, V](m: Monoid[V]): Monoid[Map[K, V]] = new Monoid[Map[K,V]] {

    override def zero: Map[K, V] = Map.empty
    override def op(x: Map[K, V], y: Map[K, V]): Map[K, V] =
      (x.keySet ++ y.keySet).foldLeft( Map.empty[K,V])((b, a) =>
        b.updated( a,
          m.op(
            x.getOrElse(a, zero),
            y.getOrElse(a, zero) )
        )
      )
  }

  def bag[A](as : IndexedSeq[A]): Map[A, Int] =
    foldMapV(as, merge[A, Int])( (a:A) => Map( a -> 1))


  //////////////////////////////////////////////////////////////////////////////////////////
  // word count
  sealed trait WC
  case class Stub(s: String) extends WC
  case class Part(l: String, n: Int, r: String) extends WC

  val wordCountMonoid: Monoid[WC] = new Monoid[WC ] {
    override def zero: WC = Stub("")
    override def op(x: WC, y: WC): WC = (x, y) match {
      case (Stub(s1), Stub(s2)) => Stub(s1 + s2)
      case (Stub(s), Part(l, n, r)) => Part( s + l, n, r)
      case (Part(l, n, r), Stub(s)) => Part( l, n, r + s)
      case (Part(l, n, r), Part(x, m, y)) => Part (l, n + m + (if((r + x).isEmpty) 0 else 1), y )
    }
  }

  def wordCount(s: String): Int = {
    def toWc (c: Char): WC = if(c.isWhitespace) Part("", 0, "") else Stub(c.toString)
    def unStub (s: String): Int = if(s.isEmpty) 0 else 1

    foldMapV(s.toIndexedSeq, wordCountMonoid)( toWc) match {
      case Stub(s) => unStub(s)
      case Part(l, n, r) => unStub(l) + unStub(r) + n
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
trait Functor[F[_]] {
  def map[A,B](fa: F[A]) (f : A => B) : F[B]
}

////////////////////////////////////////////////////////////////////////////////
trait Applicative[F[_]] extends Functor[F] { self =>

  def unit[A](a: => A): F[A]

  def map2[A,B,C](fa: F[A], fb: F[B])(f: (A,B) => C): F[C]
  //   = apply( map(fa)(f.curried))(fb)

  def apply[A,B](ff: F[A => B])(fa: F[A]): F[B]
  = map2(ff, fa)( (f, a) => f (a))

  def map[A,B](fa: F[A])(f: A => B) : F[B]
  = map2(fa, unit(f))((a, f) => f(a))

  def factor[A,B](fa: F[A], fb:F[B]): F[(A, B)]
  = map2(fa, fb)((_,_))

  // (F[A], G[A])
  def product[G[_]](G: Applicative[G]): Applicative[({ type l[x] = (F[x], G[x]) })#l] = {

    new Applicative[({type l[x] = (F[x], G[x])})#l] {
      override def unit[A](a: => A): (F[A], G[A]) = (self.unit(a), G.unit(a))
      override def map2[A, B, C](fa: (F[A], G[A]), fb: (F[B], G[B]))(f: (A, B) => C): (F[C], G[C]) =
        ( self.map2(fa._1, fb._1)(f), G.map2(fa._2, fb._2)(f))

    }
  }

  // F[G[A]]
  def composeA[G[_]](G: Applicative[G]): Applicative[({ type l[x] = F[G[x]] })#l] =
    new Applicative[({type l[x] = F[G[x]]})#l] {

      override def unit[A](a: => A): F[G[A]] = self.unit(G.unit(a))
      override def map2[A, B, C](fa: F[G[A]], fb: F[G[B]])(f: (A, B) => C): F[G[C]] =
        self.map2( fa, fb)( (ga, gb) => G.map2(ga, gb)(f))

    }

  // val m = Map (1 -> "abc".map(_.toString).toList, 2 -> "bcd".map(_.toString).toList )
  // sequenceM( m ) ==
  // List (
  //  Map(1 -> a, 2 -> b),
  //  Map(1 -> a, 2 -> c),
  //  Map(1 -> a, 2 -> d),
  //  Map(1 -> b, 2 -> b),
  //  Map(1 -> b, 2 -> c),
  //  Map(1 -> b, 2 -> d),
  //  Map(1 -> c, 2 -> b),
  //  Map(1 -> c, 2 -> c),
  //  Map(1 -> c, 2 -> d))
  def sequenceM[K,V] (ofa: Map[K, F[V]]): F[Map[K,V]] =
    (ofa foldLeft unit(Map.empty[K,V])) { case (b, (k, fv)) =>
      map2( b, fv)((m, v) => m + (k -> v))
    }
}

////////////////////////////////////////////////////////////////////////////////
object Applicative {
  ////////////////////////////////////////////////////////////////////////////////
  // List :: Traversable
  def sequence[F[_]:Applicative, A]( fas: List[F[A]]): F[List[A]]
  = traverse(fas)( identity)

  def traverse[F[_]:Applicative,A,B](as: List[A])(f: A => F[B]): F[List[B]] = {
    val F = implicitly[Applicative[F]]
    as.foldRight(F.unit(List.empty[B]))((a, b) => F.map2( f(a), b) ( _ :: _))
  }

  // replicateM --> replicateA
  def replicateM[F[_]: Applicative, A](n: Int, fa: F[A]) : F[List[A]]
  = sequence(List.fill(n)(fa))


  val streamApplicative = new Applicative[LazyList] {

    override def unit[A](a: => A): LazyList[A] = LazyList.continually(a)

    // pairwise map2::  implementing apply is not proper...
    override def map2[A, B, C](fa: LazyList[A], fb: LazyList[B])(f: (A, B) => C): LazyList[C] = {
      (fa zip fb).map( f.tupled)
    }
  }

  ////////////////////////////////////////////////////////////////////////////////
  sealed trait Validation[+E, +A]
  case class Failure[E](head : E, tail: Vector[E]) extends Validation[E, Nothing]
  case class Success[A](a : A) extends Validation[Nothing, A]

  def validationApplicative[E]: Applicative[({ type l[x] = Validation[E, x] })#l]
  = new Applicative[({type l[x] = Validation[E,x]})#l] {

    override def unit[A](a: => A): Validation[E, A] = Success(a)
    override def map2[A, B, C](fa: Validation[E, A], fb: Validation[E, B])(f: (A, B) => C): Validation[E, C]
    = (fa, fb) match {
      case (Success(a), Success(b))  => Success( f(a,b))
      case (Failure(h,t), Failure(x, y)) => Failure(h, t ++ Vector(x) ++ y)
      case (f@Failure(_,_), _) => f
      case (_, f@Failure(_,_)) => f
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
trait Monad[F[_]] extends Applicative[F] {

  import scala.language.implicitConversions
  implicit def toMonadicOp[A](fa: F[A])= new toMonadOp[F,A](fa)(this)

  def join[A](mma: F[F[A]]): F[A] =
    flatMap(mma)(identity)

  def flatMap[A,B]( fa: F[A])(f: A => F[B]): F[B] =
    join( map(fa)(f))

  override def apply[A, B](ff: F[A => B])(fa: F[A]): F[B] =
    flatMap(ff)(f => map(fa)(a => f(a)) )

  override def map[A, B](fa: F[A])(f: A => B): F[B] =
    flatMap(fa)(a => unit(f(a)))

  override def map2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] =
    flatMap(fa)(a => map(fb)(b => f(a,b)))

  def compose[A,B,C](f: A => F[B], g: B => F[C]): A => F[C] =
    a => flatMap(f(a))(g)

  // in below, List :: Traversable , F:: Applicative
  def sequence[A](lfa: List[F[A]]) : F[List[A]] =
    lfa.foldRight( unit( List.empty[A]))((a, b) => map2(a, b)( _ :: _))

  def traverse[A,B](la: List[A])(f: A => F[B]): F[List[B]] =
    la.foldRight( unit(List.empty[B]))( (a, b) => map2( f(a), b)( _ :: _ ))

  def replicateM[A](n: Int)(fa: F[A]): F[List[A]] =
    sequence( List.fill(n)(fa))

  def filterM[A](la: List[A])(p: A => F[Boolean]): F[List[A]] =
    la.foldRight(unit(List.empty[A]))( (a,b) => map2(p(a), b)( (x, y) => if(x) a :: y else y ))

  def foldM[A, B](l: LazyList[A])(z: B)(f: (B, A) => F[B]): F[B] = {
    l match {
      case h #:: t => f(z, h) flatMap (z2 => foldM(t)(z2)(f))
      case _ => unit(z)
    }
  }

  def as[A,B](fa: F[A])(b: B): F[B] =
    map(fa)(_ => b)

  def skip[A](fa: F[A]): F[Unit] =
    as(fa)(())

  def when[A](b: Boolean)(fa: => F[A]): F[Boolean] =
    if(b) as(fa)(true) else unit(false)

  // todo
//  def forever[A,B](fa: F[A]): F[B] = {
//    lazy val b: F[B] = flatMap(fa)(_ => b)
//    b
//  }

  // todo
//  def while_(fa: F[Boolean])(b: F[Unit]): F[Unit] = {
//    lazy val t: F[Unit] = while_(fa)(b)
//    fa flatMap (c => skip(when(c)(t)))
//  }

  // todo
//  def doWhile[A](fa: F[A])(cond: A => F[Boolean]): F[Unit] = for {
//    a <- fa
//    ok <- cond(a)
//    _ <- if(ok) doWhile(fa)(cond) else unit(())
//  } yield ()
}

////////////////////////////////////////////////////////////////////////////////
object Monad {

  implicit class toMonadOp[F[_]:Monad, A]( fa: F[A]) {

    val F: Monad[F] = implicitly[Monad[F]]

    def map[B](f: A => B): F[B] = F.map(fa)(f)
    def flatMap[B](f: A => F[B]): F[B] = F.flatMap(fa)(f)

    def **[B](b: F[B]): F[(A, B)] = F.map2(fa,b)((_,_))
    def *>[B](b: F[B]): F[B] = F.map2(fa,b)((_, b) => b)
    def map2[B,C](b: F[B])(f: (A,B) => C): F[C] = F.map2(fa,b)(f)
    def as[B](b: B): F[B] = F.as(fa)(b)
    def skip: F[Unit] = F.skip(fa)

    def replicateM(n: Int): F[List[A]] = F.replicateM(n)(fa)
  }

  ////////////////////////////////////////////////////////////////////////////////
  //// Either Monad
  def eitherMonad[E]: Monad[({ type l[x] = Either[E, x] })#l] =

    new Monad[({type l[x] = Either[E,x]})#l] {
      override def unit[A](a: => A): Either[E, A] = Right(a)
      override def flatMap[A, B](fa: Either[E, A])(f: A => Either[E, B]): Either[E, B] = fa match {
        case Left(e) => Left(e)
        case Right(a) => f(a)
      }
    }

  ////////////////////////////////////////////////////////////////////////////////
  //// State Monad
  case class State[S,A](runState: S => (A,S))
  def getState[S] : State[S,S] = State( s => (s, s))
  def setState[S](s: S) : State[S,()] = State( _ => ((), s))

  implicit def stateMonad[S]: Monad[({ type l[x] = State[S, x] })#l] =
    new Monad[({ type l[x] = State[S,x] } )#l] {
      override def unit[A](a: => A): State[S, A] = State( s => (a,s ))

      override def flatMap[A, B](fa: State[S, A])(f: A => State[S, B]): State[S, B] =
        State( s0 => {
          val (a, s1) = fa.runState(s0)
          f(a).runState(s1)
        })
    }

  //// zipWithIndex with State
  def zipWithIndex[A](as: List[A]) : List[(Int,A)] = {

    implicit val M = stateMonad[Int]

    val st = as.foldLeft( M.unit(List.empty[(Int,A)]))( (b, a) => for {
        xs <- b
        n <- getState[Int]
        _ <- setState(n +1 )
      } yield ( (n, a) :: xs) )

    st.runState(0)._1.reverse
  }

  ////////////////////////////////////////////////////////////////////////////////
  //// reader Monad
  case class Reader[R,A]( runReader: R => A)

  def readerMonad[R]: Monad[({ type l[a] = Reader[R, a] })#l] = new Monad[({type l[a] = Reader[R,a]})#l] {

    override def unit[A](a: => A): Reader[R, A] = Reader( _ => a)
    override def flatMap[A, B](fa: Reader[R, A])(f: A => Reader[R, B]): Reader[R, B] = Reader ( r0 => {
      val a = fa.runReader(r0)
      f(a).runReader(r0)
    })
  }

  ////////////////////////////////////////////////////////////////////////////////
  //// Id Monad
  case class Id[A] ( value: A)

  def IdMonad: Monad[Id] = new Monad[Id] {
    override def unit[A](a: => A): Id[A] = Id( a)
    override def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] = f(fa.value)
  }
  //////////////////
  def composeM[F[_], G[_]]( implicit F: Monad[F], G: Monad[G], T: Traverse[G])
  : Monad[({ type l[x] = F[G[x]] })#l] =

    new Monad[({type l[x] = F[G[x]]})#l] {

      override def unit[A](a: => A): F[G[A]] =  F.unit(G.unit(a))

       // todo
      override def flatMap[A, B](fga: F[G[A]])(f: A => F[G[B]]): F[G[B]] =
        F.flatMap(fga)(ga => {
          F.map( T.traverse(ga)(f))(G.join)
        } )
    }
}


////////////////////////////////////////////////////////////////////////////////
trait Traverse[F[_]] extends Functor[F] with Foldable[F] { self =>

  // traverse vs foldMap
  def traverse2[G[_]:Applicative ,A,B](list: List[A])(f: A => G[B]): G[List[B]]
  = {
    val Ap = implicitly[Applicative[G]]
    list.foldRight( Ap.unit( List.empty[B]))(( a,b) => Ap.map2(f(a), b)( _ :: _)  )
          //
          // 1. traverse all element of Traversable
                  // 2.  Empty-Traversable is lifted to Ap.
                                                      // 3. each element restructured by Traversbale is lifed to Ap.

//    list.foldRight( M.zero                 )( (a,b) => M.op( (f(a), b)))         // foldMap

  }
  // G :: map2,  F:: Foldable + construct
  def traverse[G[_]:Applicative,A,B](fa: F[A])(f : A => G[B]): G[F[B]]
  = sequence( map(fa)(f))

  def sequence[G[_]: Applicative,A](fma: F[G[A]]) : G[F[A]]
  = traverse(fma)(identity)

  ////////////////////////////////////////////////////////////////////////////////
  type Id[A] = A
  val idMonad = new Monad[Id] {
    override def unit[A](a: => A): Id[A] = a
    override def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] = f(fa)
  }

  override def map[A,B](fa: F[A])(f: A => B): F[B] =
    traverse[Id, A,B](fa)(f)(idMonad)

  ////////////////////////////////////////////////////////////////////////////////

  // see: https://typelevel.org/cats/datatypes/const.html

  type Const[A,B] = A

  def constApplicative[M](M : Monoid[M])
  : Applicative[({ type l[x] = Const[M, x] })#l] = new Applicative[({type l[x] = Const[M, x]})#l] {

    override def unit[A](a: => A) : Const[M, A] = M.zero
    override def map2[A, B, C]( fa: Const[M, A], fb: Const[M, B])(f: (A, B) => C) : Const[M, C] = M.op( fa, fb)
  }

  override def foldMap[A, B](as: F[A], mb: Monoid[B])(f: A => B): B =
    sequence[({type f[x] = Const[B,x]})#f, B](map(as)(f))(constApplicative(mb))

  ////////////////////////////////////////////////////////////////////////////////
  def fuse[M[_],N[_],A,B](fa: F[A])(f: A => M[B], g: A => N[B])
                         (implicit M: Applicative[M], N: Applicative[N]): (M[F[B]], N[F[B]]) = {
    traverse[({type l[x] = (M[x], N[x])})#l, A, B](fa)( a => (f(a),g(a)))( M product  N)
  }

  def composeT[G[_]: Traverse] =
    new Traverse[({type l[x] = F[G[x]]})#l] {
      val G = implicitly[Traverse[G]]
      override def traverse[M[_] : Applicative, A, B](fga: F[G[A]])(f: A => M[B]): M[F[G[B]]] = {
        self.traverse(fga)( ga => G.traverse(ga)(f))
      }
    }

  ////////////////////////////////////////////////////////////////////////////////
  // stateful traverse
  ////////////////////////////////////////////////////////////////////////////////
  def traverseSt[S,A,B](fa: F[A])(f: A => State[S,B]): State[S, F[B]] =
    traverse[({type l[x] = State[S,x]})#l,A,B](fa)(f)(stateMonad)

  def zipWithIndex[A](fa: F[A]): F[(A,Int)] =
    traverseSt(fa)((a: A) =>
      for {
        i <- getState[Int]
        _ <- setState(i +1)
      } yield a -> i
    ).runState(0)._1

  def toList_[A](fa: F[A]):List[A] = {

    val ret =
      traverseSt(fa)(a => for {
        as <- getState[List[A]]
        _ <- setState( a :: as)
      } yield() ).runState(Nil)._2

    ret
  }

  def mapAccum[S, A, B](fa: F[A], s: S)(f: (A, S) => (B, S)): (F[B], S) =
    traverseSt(fa)((a: A) => (for {
      s1 <- getState[S]
      (b, s2) = f(a, s1)
      _ <- setState(s2)
    } yield b)).runState(s)

  override def toList[A](fa: F[A]): List[A] =
    mapAccum(fa, List[A]())((a, s) => ((), a :: s))._2.reverse

  def zipWithIndex[A](fa: F[A]): F[(A, Int)] =
    mapAccum(fa, 0)((a, s) => ((a, s), s + 1))._1

  def reverse[A](fa: F[A]): F[A] =
    mapAccum(fa, toList(fa).reverse)((_, as) => (as.head, as.tail))._1

  override def foldLeft[A, B](fa: F[A])(z: B)(f: (B, A) => B): B =
    mapAccum(fa, z)((a, b) => ((), f(b, a)))._2

  def zip[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    (mapAccum(fa, toList(fb)) {
      case (a, Nil) => sys.error("zip: Incompatible shapes.")
      case (a, b :: bs) => ((a, b), bs)
    })._1

  def zipL[A, B](fa: F[A], fb: F[B]): F[(A, Option[B])] =
    (mapAccum(fa, toList(fb)) {
      case (a, Nil) => ((a, None), Nil)
      case (a, b :: bs) => ((a, Some(b)), bs)
    })._1

  def zipR[A, B](fa: F[A], fb: F[B]): F[(Option[A], B)] =
    (mapAccum(fb, toList(fa)) {
      case (b, Nil) => ((None, b), Nil)
      case (b, a :: as) => ((Some(a), b), as)
    })._1
}

object Traverse {

  val listTraverse = new Traverse[List] {
    override def traverse[G[_] : Applicative, A, B](fa: List[A])(f: A => G[B]): G[List[B]] = {
      val G = implicitly[Applicative[G]]
      fa.foldRight( G.unit( List.empty[B]))((a, b) => G.map2( f(a),b) ( _ :: _))
    }
  }

  val optionTraverse = new Traverse[Option] {
    override def traverse[G[_] : Applicative, A, B](fa: Option[A])(f: A => G[B]): G[Option[B]] = {
      val G = implicitly[Applicative[G]]
      fa.map(f) match {
        case None => G.unit(None)
        case Some(ga) => G.map(ga)(Option(_))
      }
    }
  }

    case class Tree[+A](head: A, tail: List[Tree[A]])

    val treeTraverse = new Traverse[Tree] {
      override def traverse[G[_] : Applicative, A, B](fa: Tree[A])(f: A => G[B])
      : G[Tree[B]] = {
        val G = implicitly[Applicative[G]]
        G.map2( f(fa.head), listTraverse.traverse(fa.tail)(a => traverse(a)(f)))(Tree( _, _))
      }
    }

    /// foldable but not-functor
    case class Iteration[A](seed: A, call: A => A, count: Int) {

      def foldMap[B](f: A => B)(M: Monoid[B]): B = {

        def iterate(n: Int, b: B, a: A): B =
          if (n <= 0)
            b
          else
            iterate(n - 1, f(a), call(seed))

        iterate(count, M.zero, seed)
      }

      def map[B](g: A => B): Iteration[B] = ??? // impossible.. because of f : A =>
    }

}
