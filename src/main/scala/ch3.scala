import scala.annotation.tailrec

object ch3 {

  /** Note {{{
   *
    functor -------------------
      def unit[A] ( a: => A): F[A]
      def map[A,B](a: F[A])( f: A => B) : F[B]

    applicative ---------------
      def ap[A,B](a: F[A])( f: F[A => B]) : F[B]
      def map2[A,B,C](a: F[A])(b: F[B])(f: (A,B) => C) : F[C]

    monad ---------------------
      def flatMap[A,B](a: F[A])(f: A => F[B]): F[B]

    monoid -------------------
      def fold[A,B](as: F[A])(z: B)(f: (A,B) => B ): B
  }}}
  */

  sealed trait SList[+A] {

    override def toString: String = this match {
      case Cons (h, t) => s"($h, $t)"
      case _ => "Nil"
    }

  }

  case object Nil extends SList[Nothing]
  case class Cons[+A]( head: A, tail: SList[A]) extends SList[A]

  object SList {

    implicit class SListWrapper[A](as: SList[A]) {
      def filter2(p: A => Boolean): SList[A] = SList.filter2(as)(p)

      def length(): Int = SList.length(as)

      def foldRight[B](z: B)(f: (A, B) => B): B = SList.foldRight(as)(z)(f)

      def map[B](f: A => B): SList[B] = SList.map(as)(f)
    }

    // unit
    def apply[A](a: A*): SList[A] = {
      //      if (a.isEmpty) Nil else Cons(a.head, SList(a.tail: _*))
      a.foldRight[SList[A]](Nil)((a, b) => Cons(a, b))
    }

    // exercise 3.2
    def tail2[A](as: SList[A]): SList[A] = as match {
      case Nil => sys.error("tail on empty")
      case Cons(_, t) => t
    }

    // exercise 3.3
    def setHead[A](as: SList[A])(a: A): SList[A] = as match {
      case Nil => sys.error("setHead on empty")
      case Cons(_, t) => Cons(a, t)
    }

    // exercise 3.4
    @tailrec
    def drop[A](as: SList[A])(n: Int): SList[A] =
      if (n <= 0) as
      else as match {
        case Nil => Nil
        case Cons(_, t) => drop(t)(n - 1)
      }

    // exercise 3.5
    @tailrec
    def dropWhile[A](as: SList[A])(p: A => Boolean): SList[A] = as match {
      case Cons(h, t) if p(h) => dropWhile(t)(p)
      case _ => as
    }

    // 3.3.1
    def append[A](as: SList[A])(o: SList[A]): SList[A] = as match {
      case Cons(h, t) => Cons(h, append(t)(o))
      case Nil => o
    }

    //exercise 3.6
    def init[A](as: SList[A]): SList[A] = as match {
      case Nil => sys.error("init on empty")
      case Cons(_, Nil) => Nil
      case Cons(h, t) => Cons(h, init(t))
    }

    // with buffer
    def init2[A](as: SList[A]): SList[A] = {

      val buf = new collection.mutable.ListBuffer[A]

      @tailrec
      def go(cur: SList[A]): SList[A] = cur match {
        case Nil => sys.error("init on empty")
        case Cons(_, Nil) => SList(buf.toList: _*)
        case Cons(h, t) =>
          buf += h
          go(t)
      }

      go(as)
    }


    // listing 3.2
    def foldRight[A, B](as: SList[A])(z: B)(f: (A, B) => B): B = as match {
      case Nil => z
      case Cons(h, t) => f(h, foldRight(t)(z)(f))
    }

    // exercise 3.7, 3.8 .... skip

    // exercise 3.9
    def length[A](as: SList[A]): Int = foldRight(as)(0)((_, b) => 1 + b)

    // exercise 3.10
    @tailrec
    def foldLeft[A, B](as: SList[A])(z: B)(f: (B, A) => B): B = as match {
      case Nil => z
      case Cons(h, t) => foldLeft(t)(f(z, h))(f)
    }

    // exercise 3.11
    def sum(as: SList[Int]): Int = foldLeft(as)(0)(_ + _)

    def product(as: SList[Double]): Double = foldLeft(as)(1.0)(_ * _)

    // exercise 3.12
    def reverse[A](as: SList[A]): SList[A] = foldLeft(as)(Nil: SList[A])((b, a) => Cons(a, b))

    // exercise 3.13
    def foldRightWithLeft[A, B](as: SList[A])(z: B)(f: (A, B) => B): B =
      foldLeft(reverse(as))(z)((b, a) => f(a, b))

    // with function-chaining
    def foldRightWithLeft2[A, B](as: SList[A])(z: B)(f: (A, B) => B): B = {

      // make function: B => B
      val gs = as.map(a => (b: B) => f(a, b))

      // fold functions
      val g = foldLeft(gs)((b: B) => b)((gz, ga) => b => gz(ga(b))) // can cause stack-overflow

      g(z)

      // merged version
      foldLeft(as)((b: B) => b)((gz, a) => b => gz(f(a, b)))(z) // can cause stack-overflow
    }


    // exercise 3.15
    def concat[A](as: SList[SList[A]]): SList[A] = as.foldRight(Nil: SList[A])((a, b) => append(a)(b))

    // exercise 3.16
    def addOne(is: SList[Int]): SList[Int] =
      foldRight(is)(Nil: SList[Int])((a, b) => Cons(a + 1, b))

    // exercise 3.17
    def double2String(ds: SList[Double]): SList[String] =
      foldRight(ds)(Nil: SList[String])((a, b) => Cons(a.toString, b))

    // exercise 3.18
    def map[A, B](as: SList[A])(f: A => B): SList[B] =
      foldRight(as)(Nil: SList[B])((a, b) => Cons(f(a), b))

    // with buffer
    def mapWithBuffer[A, B](as: SList[A])(f: A => B): SList[B] = {
      val buf = new collection.mutable.ListBuffer[B]

      @tailrec
      def go(cur: SList[A]): SList[B] = cur match {
        case Nil => SList( buf.toList: _*)
        case Cons(h, t) =>
          buf += f(h)
          go(t)
      }
      go(as)
    }


    // exercise 3.19
    def filter[A](as: SList[A])(p: A => Boolean): SList[A] =
      foldRight(as)(Nil: SList[A])((a, b) => if (p(a)) Cons(a, b) else b)

    // exercise 3.20
    def flatMap[A, B](as: SList[A])(f: A => SList[B]): SList[B] =
      concat(as.map(f))

    // exercise 3.21
    def filter2[A](as: SList[A])(p: A => Boolean): SList[A] =
      flatMap(as)(a => if (p(a)) SList(a) else Nil)

    // exercise 3.22
    def zipSum(as: SList[Int])(bs: SList[Int]): SList[Int] = (as, bs) match {
      case (Nil, _) => Nil
      case (_, Nil) => Nil
      case (Cons(h1, t1), Cons(h2, t2)) => Cons(h1 + h2, zipSum(t1)(t2))
    }

    // exercise 3.23
    def zipWith[A, B, C](as: SList[A])(b: SList[B])(f: (A, B) => C): SList[C] = (as, b) match {
      case (Nil, _) => Nil
      case (_, Nil) => Nil
      case (Cons(h1, t1), Cons(h2, t2)) => Cons(f(h1, h2), zipWith(t1)(t2)(f))
    }


    // listing 3.4.1
    //    def take[A](as: SList[A])(n: Int) : SList[A] = ???
    //    def takeWhile[A](as: SList[A])(p: A => Boolean) : SList[A] = ???
    //    def forAll[A](as: SList[A])(p: A => Boolean) : SList[A] = ???
    //    def exists[A](as: SList[A])(p: A => Boolean) : SList[A] = ???
    //    def scanLeft[A](as: SList[A])(p: A => Boolean) : SList[A] = ???
    //    def scanRight[A](as: SList[A])(p: A => Boolean) : SList[A] = ???


    @tailrec
    def startWith[A](as: SList[A])(bs: SList[A]): Boolean = (as, bs) match {
      case (_, Nil) => true
      case (Cons(h1, t1), Cons(h2, t2)) if h1 == h2 => startWith(t1)(t2)
      case _ => false
    }

    // exercise 3.24
    @tailrec
    def hasSubsequence[A](as: SList[A], sub: SList[A]): Boolean = as match {
      case Nil => sub == Nil
      case _ if startWith(as)(sub) => true
      case Cons(_, t) => hasSubsequence(t, sub)
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////
  sealed trait Tree[+A]
  case class Leaf[A] ( value: A) extends Tree[A]
  case class Branch[A] ( left: Tree[A], right: Tree[A]) extends Tree[A]

  object Tree {
    def unit[A]( a: A) : Tree[A] = Leaf(a)

    // exercise 3.25
    def  size[A](tr: Tree[A]): Int = tr match {
      case Leaf(_) => 1
      case Branch(l, r) => size(l) + size(r) + 1
    }

    // exercise 3.26
    def maxInt(tr: Tree[Int]): Int = tr match {
      case Leaf(a) => a
      case Branch(l, r) => maxInt(l) max maxInt(r)
    }

    // exercise 3.27
    def depth[A](tr: Tree[A]): Int = tr match {
      case Leaf(a) => 1
      case Branch(l, r) => 1 + (depth(l) max depth(r))
    }

    // exercise 3.28
    def map[A,B](tr: Tree[A])(f: A => B): Tree[B] = tr match {
      case Leaf(a) => Leaf(f(a))
      case Branch(l, r) => Branch(map(l)(f), map(r)(f))
    }

    // exercise 3.29
    def fold[A,B](tr: Tree[A])(f: A => B)(g: (B, B) => B): B = tr match {
      case Leaf(a) => f(a)
      case Branch(l, r) => g( fold(l)(f)(g), fold(r)(f)(g))
    }

    def mapWithFold[A,B](tr: Tree[A])(f: A => B): Tree[B] =
      fold(tr)(a => Leaf(f(a)): Tree[B])( Branch(_, _) )

    def depthWithFold[A](tr: Tree[A]): Int =
      fold(tr)(_ => 1)((l, r) => 1 + (l max r))

    def maxIntWithFold(tr: Tree[Int]): Int =
      fold(tr)(identity)((l, r) => l max r)

    def sizeWithfold[A](tr: Tree[A]): Int =
      fold(tr)( _ => 1)((l, r) => 1 + l + r )

  }


  def main(args: Array[String]): Unit = {

    // exercise 3.1
    val x = SList(1,2,3,4,5) match {
      case Cons(x, Cons(2, Cons(4,_))) => x
      case Nil => 42
      case Cons(x, Cons(y, Cons(3, Cons(4, _)))) => x + y   // --> this
      case _ => 101
    }
    println(x)

    val f = SList(0 to 100: _*).filter2( _ % 2 != 0)
    println( f )
    println( f.length() )
  }
}
