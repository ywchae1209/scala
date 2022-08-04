import scala.annotation.tailrec

object ch2 {

  // exerciese 2.1
  def fib(n: Int): Int = {

    @tailrec
    def go(a: Int, b: Int, c: Int): Int =
      c match {
        case 0 => a
        case 1 => b
        case _ => go(b, a+b , c-1)
      }

    go(0, 1, n)
  }

  // exercise 2.2
  @tailrec
  def isSorted[A]( as: Seq[A], ordered: (A, A) => Boolean): Boolean = {
      if( !as.isDefinedAt(1)) true
      else if( !ordered(as.head, as(1)) ) false
      else isSorted( as.tail, ordered)
  }

  // list 2.4
  @tailrec
  def findFirst[A]( as: Seq[A], p: A => Boolean): Option[A] = {
    if( as.isEmpty) None
    else if( p(as.head ) ) Some( as.head)
    else findFirst( as.tail, p)
  }

  // 2.6
  def partial[A,B,C]( a: A, f: (A,B) => C): B => C = b => f(a, b)

  // exercise 2.3
  def curry[A,B,C](f: (A, B) => C): A => B => C = a => b => f(a, b)

  // exercise 2.4
  def uncurry[A,B,C](f: A => B => C): (A, B) => C = (a, b) => f(a)(b)

  // exercise 2.4
  def compose[A,B,C](f: B => C, g: A => B): A => C = a => f(g(a))


  def main(args: Array[String]): Unit = {
    (0 to 10 ) foreach { i =>
      println( s"$i \t:\t${fib(i)}")
    }

    val s =Seq(1,2,3,4,5,6,7)
    val s1 =Seq(1,2,3,7,5,6,7)
    println( s"sorted: ${isSorted(s, (a:Int,b:Int) => a < b)}, $s")
    println( s"sorted: ${isSorted(s1, (a:Int,b:Int) => a < b)}, $s1")

    println( s"find == 5: ${findFirst(s1, (a:Int) => a == 5)} from $s1")
    println( s"find == 9: ${findFirst(s1, (a:Int) => a == 9)} from $s1")

  }
}
