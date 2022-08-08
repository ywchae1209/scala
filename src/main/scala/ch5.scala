import ch5.LazySeq.{cons, empty, fibs_2, unfold}

import scala.annotation.tailrec

object ch5 {

  sealed trait LazySeq[+A] {

    override def toString: String = this match {
      case Empty => ""
      case Cons( h, t) => s"${h()}, ${t()}"
    }

    //// exercise 5.1
    def toList: List[A] =
      this match {
        case Empty => Nil
        case Cons(h, t) => h() +: t().toList
      }

    // buffer version
    def toListWithBuffer: List[A] = {
      val buf = new collection.mutable.ListBuffer[A]

      @tailrec
      def go( cur: LazySeq[A]): List[A] = cur match {

        case Empty => buf.toList
        case Cons(h, t) => buf += h(); go(t())
      }

      go(this)
    }

    // exercise 5.2
    def take(n: Int): LazySeq[A] = this match {
      case Cons(h, t) if n > 1  => cons(h(), t().take(n-1))
      case Cons(h, _) if n == 1 => cons(h(), empty)
      case _ => empty
    }

    // exercise 5.3
    def drop(n: Int): LazySeq[A] = this match {
      case Cons(_, t) if n > 0 => t().drop(n-1)
      case Empty => empty
    }

    // exercise 5.3, 5.4
    def takeWhile(p: A => Boolean): LazySeq[A] =
      foldRight(empty[A])((a, b) => if(p(a)) cons(a, b) else empty)

    def dropWhile(p: A => Boolean): LazySeq[A] =
      foldRight(empty[A])((a, b) => if(p(a)) b else cons(a, b))

    // Listing
    def exists(p: A => Boolean): Boolean =
      foldRight(false)( (a,b) => p(a) || b )

    // exercise 5.5
    def forAll(p: A => Boolean): Boolean =
      foldRight(true)( (a,b) => p(a) && b )

    // exercise 5.6
    def headOption: Option[A] =
      foldRight(None: Option[A])( (a, _) => Some(a) )

    // exercise 5.7
    def map[B](f: A => B) : LazySeq[B] =
      foldRight(empty[B])((a,b) => cons(f(a), b) )

    def filter(p: A => Boolean): LazySeq[A] =
      foldRight(empty[A])((a,b) => if(p(a)) cons(a, b) else b)

    def append[B>:A](o: LazySeq[B]): LazySeq[B] =
      foldRight(o)((a,b) => cons(a, b))

    def flatMap[B](f: A => LazySeq[B]): LazySeq[B] =
      foldRight(empty[B])((a,b) => f(a).append(b))

    // exercise 5.13
    // map, take, takeWhile, zipWith, zipAll
    def mapWithUnfold[B](f: A => B) : LazySeq[B] =
      unfold(this){
        case Empty => None
        case Cons(h, t) => Some( f(h()) -> t())
      }

    def takeWithUnfold(n: Int): LazySeq[A] =
      unfold((this, n)) {
        case (Cons(h, t), i) if i > 0 => Some( h() -> ( t() -> (i-1)))
        case _ => None
      }

    def takeWhileWithUnfold(p: A => Boolean): LazySeq[A] =
      unfold(this){
        case Cons(h,t) if p(h()) => Some( h() -> t())
        case _ => None
      }

    def zipWith[B,C](o: LazySeq[B])(f: (A,B) => C): LazySeq[C] =
      unfold((this, o)){
        case (Cons(h1, t1), Cons(h2,t2)) => Some( f(h1(), h2()) -> ( (t1(), t2()) ))
        case _ => None
      }

    def zip[B](o: LazySeq[B]): LazySeq[(A,B)] =
      zipWith(o)( _ -> _)

    def zipAllWith[B,C](o: LazySeq[B])(f: (Option[A],Option[B]) => C): LazySeq[C] =
      unfold((this,o)){
        case (Cons(h1, t1), Cons(h2,t2)) => Some( f(Some(h1()), Some(h2())) -> ( (t1(), t2()) ))
        case (Cons(h1, t1), Empty)       => Some( f(Some(h1()), None) -> ( (t1(), empty) ))
        case (Empty, Cons(h2,t2))        => Some( f(None, Some(h2()) ) -> ( (empty, t2()) ))
        case (Empty, Empty)              => None
      }

    def zipAll[B](o: LazySeq[B]): LazySeq[(Option[A], Option[B])] =
      zipAllWith(o)( _ -> _ )

    def startWith[B](o: LazySeq[B]): Boolean =
      zipAll(o).takeWhile( _._2.nonEmpty).forAll{ case (a, b) => a == b }

    def tails: LazySeq[LazySeq[A]] =
      unfold(this){
        case Empty => None
        case s => Some((s, s.drop(1)))
      }.append( empty)

    def scanRight[B](z: B)(f: (A, =>B) => B): LazySeq[B] =
      foldRight((z, LazySeq(z)))( (a, p0) => {
        lazy val p1 = p0
        val b2 = f(a, p1._1)
        (b2, cons(b2, p1._2))
      } )._2



    def hasSubsequence[B](o: LazySeq[B]): Boolean =
      tails.exists( _.startWith(o))



    ////////////////////////////////////////////////////////////////
    def foldRight[B](z: B)(f: (A, B) => B): B = this match {
      case Empty => z
      case Cons(h, t) => f(h(), t().foldRight(z)(f))
    }

    @tailrec
    final def foldLeft[B](z: B)(f: (B, A) => B): B = this match {
      case Empty => z
      case Cons(h, t) => t().foldLeft( f(z, h()))(f)
    }
  }

  case object Empty extends LazySeq[Nothing]
  case class Cons[+A]( h: () => A, t : () => LazySeq[A]) extends LazySeq[A]

  object LazySeq {

    def cons[A]( h: => A, t: => LazySeq[A]): LazySeq[A] = {
      lazy val head = h
      lazy val tail = t
      Cons( () => head, () => tail)
    }

    def empty[A]: LazySeq[A] = Empty

    def apply[A](as: A*): LazySeq[A] =
      if( as.isEmpty) empty else cons( as.head, LazySeq(as.tail: _*))

    val ones: LazySeq[Int] = cons(1, ones)

    // exercise 5.8
    def constant[A](a: A) : LazySeq[A] = {
      lazy val n : LazySeq[A] = Cons( () => a, () => n)
      n
    }

    // exercise 5.9
    def from(n: Int): LazySeq[Int] =
      cons(n, from(n+1))

    // exercise 5.10
    def fibs: LazySeq[Int] = {

      def go(i: Int, j: Int): LazySeq[Int] =
        cons( i, go(j, i+j))

      go(0, 1)
    }

    // exercise 5.11
    def unfold[S,A](z: S)(f: S => Option[(A, S)]): LazySeq[A] =
      f(z) match {
        case None => empty
        case Some((a,s)) => cons(a, unfold(s)(f))
      }

    def unfoldWithMap[S,A](z: S)(f: S => Option[(A, S)]): LazySeq[A] =
      f(z).map( as => cons(as._1, unfoldWithMap(as._2)(f))).getOrElse(empty[A])

    def unfoldWithFold[S,A](z: S)(f: S => Option[(A, S)]): LazySeq[A] =
      f(z).fold( empty[A])( as => cons( as._1, unfoldWithFold(as._2)(f)))


    // exercise 5.12
    val ones_2: LazySeq[Int] =
      unfold(1)( _ => Some(1 -> 1))

    def constant_2[A](a: A): LazySeq[A] =
      unfold(a)(_ => Some(a, a))

    def from_2(n: Int): LazySeq[Int] =
      unfold(n)(i => Some( i -> (i+1) ))

    def fibs_2: LazySeq[Int] =
      unfold( (0,1) ){ case (i, j) => Some(i, (j, (i+j) ) ) }

  }

  def main(arg: Array[String]): Unit = {
    // listing 5.1
    { println("\nbefore 'false &&'"); false } && { println("after 'false &&'"); true }
    { println("\nbefore 'true &&'"); true } && { println("after 'true &&'"); true }

    { println("\nbefore 'false ||'"); false } || { println("after 'false ||'"); true }
    { println("\nbefore 'true ||'"); true } || { println("after 'true ||'"); true }

    ////////////////////////////////////////////////////////////////////////////////
    def if2[A](cond:Boolean, onTrue: => A, onFalse: => A): A = if(cond) onTrue else onFalse
    if2( cond = true, println("a"), println("b"))
    if2( cond = false, sys.error("onTrue"), println("b"))

    ////////////////////////////////////////////////////////////////////////////////
    def twice(b: Boolean, i: => Int) = if(b) i + i else 0
    twice(true, { println("hi"); 5 })
    // hi
    // hi

    def twice2(b: Boolean, i: => Int) = {
      lazy val j = i
      if(b) j + j else 0
    }
    twice2(true, { println("hi"); 5 })
    // hi

    println( LazySeq(1,2,3).take(2).toList)
    println( fibs_2.takeWhileWithUnfold( _ < 10))
  }
}
