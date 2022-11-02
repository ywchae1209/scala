package recursion

object Trampoline {

  sealed trait TailRec[A] {
    def flatMap[B](f: A => TailRec[B]): TailRec[B] = FlatMap(this, f)
    def map[B](f: A => B): TailRec[B] = flatMap( a => Just(f(a)))
    def run: A = Trampoline.run(this)
  }

  def map2[A,B,C](a: TailRec[A], b: TailRec[B])(f: (A,B) => C)
  : TailRec[C] = a.flatMap(a0 => b.map(b0 => (f(a0, b0))))

  ////////////////////////////////////////////////////////////////////////////////
  def pure[A](a : => A): TailRec[A] = Just(a)
  def suspend[A](a : => TailRec[A]): TailRec[A] = Suspend( () => a)

  ////////////////////////////////////////////////////////////////////////////////
  private case class Just[A](a: A) extends TailRec[A]
  private case class Suspend[A](stub: () => TailRec[A]) extends TailRec[A]
  private abstract case class FlatMap[B]() extends TailRec[B] {
    type A                    // walk-around for existential type erasure
    val a: TailRec[A]
    val cont: A => TailRec[B]
  }

  private object FlatMap {
    def apply[A0,B]( a0: TailRec[A0], cont0: A0 => TailRec[B]) =
      new FlatMap[B] {
        type A = A0
        val a = a0
        val cont = cont0
    }
  }
  ////////////////////////////////////////////////////
  @scala.annotation.tailrec
  def run[A](tailrec: TailRec[A]): A = tailrec match {
    case Just(a)     => a
    case Suspend(r)  => run(r())
    case f@FlatMap() => f.a match {
      case Just(a)     => run(f.cont(a))
      case Suspend(s)  => run(s().flatMap (f.cont))
      case g@FlatMap() => run( g.a.flatMap(a => g.cont(a).flatMap(f.cont)) )
    }
  }
}

object TrampolineTest extends App {

  import Trampoline._

  def sum(n: Int): TailRec[BigInt] =
    if( n <= 0 ) pure(0)
    else suspend( map2(pure(n), sum(n-1)){(i, j) => println( s"$i + $j = ${i + j}"); i + j})

  val prog =
    for {
      _ <- suspend( pure( print("Please enter a number: ")))
      i <- pure(scala.util.Try(scala.io.StdIn.readLine().toInt).toOption)
      _ <- i.fold( pure(println("Invalid number")) ){
        n => sum(n).map(result => println(s"sum of 1..$n is $result"))
      }
    } yield ()

  prog.run
  //////////////////////////////

  val ex0 = List.fill(100000)(1).foldLeft(pure(0))((b, a) => suspend( map2(b, pure(a))( _ + _)))
  println(ex0.run)

}
