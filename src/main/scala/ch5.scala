object ch5 {

  sealed trait LazySeq[+A]
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

  }

  def main(arg: Array[String]): Unit = {
    // listing 5.1
    { println("\nbefore 'false &&'"); false } && { println("after 'false &&'"); true }
    { println("\nbefore 'true &&'"); true } && { println("after 'true &&'"); true }

    { println("\nbefore 'false ||'"); false } || { println("after 'false ||'"); true }
    { println("\nbefore 'true ||'"); true } || { println("after 'true ||'"); true }

    ////////////////////////////////////////////////////////////////////////////////
    def if2[A](cond:Boolean, onTrue: => A, onFalse: => A): A = if(cond) onTrue else onFalse
    if2( true, println("a"), println("b"))
    if2( false, sys.error("onTrue"), println("b"))

    ////////////////////////////////////////////////////////////////////////////////
    def maybeTwice(b: Boolean, i: => Int) = if(b) i + i else 0
    maybeTwice(true, { println("hi"); 5 })
    // hi
    // hi

    def maybeTwice2(b: Boolean, i: => Int) = {
      lazy val j = i
      if(b) j + j else 0
    }
    maybeTwice2(true, { println("hi"); 5 })
    // hi

  }

}
