package recursion

import scala.annotation.tailrec

object Trampoline {

  sealed trait TailRec[A] {
    final def flatMap[B](f: A => TailRec[B]): TailRec[B] = FlatMap(this, f)
    final def map[B](f: A => B): TailRec[B] = flatMap( a => Pure(f(a)))
    final def run: A = Trampoline.run(this)

    // evaluate a single layer
    @tailrec
    final def resume: Either[TailRec[A], A] = this match {
      case Pure(a) => Right(a)
      case Suspend(a) => a().resume
      case x@FlatMap(_,_) => x.a match {
        case Pure(a) => x.cont(a).resume
        case Suspend(a) => a().flatMap(x.cont).resume
        case y@FlatMap(_,_) => y.a.flatMap( a => y.cont(a).flatMap(x.cont)).resume
      }
    }
  }
  def map2[A,B,C](a: TailRec[A], b: TailRec[B])(f: (A,B) => C)
  : TailRec[C] = a.flatMap(a0 => b.map(b0 => (f(a0, b0))))

  ////////////////////////////////////////////////////////////////////////////////
  def pure[A](a : => A): TailRec[A] = Pure(a)
  def suspend[A](a : => TailRec[A]): TailRec[A] = Suspend( () => a)
  ////////////////////////////////////////////////////////////////////////////////
  /*
   * Analogy to Free
   *  type Trampoline[+A] = Free[Function0, A]
   *       Pure    ::: A
   *       Suspend ::: F[Free[F,A]]
   *       FlatMap ::: Free[F,A], A => Free[F,B]
   */
  private case class Pure[A](a: A) extends TailRec[A]
  private case class Suspend[A](stub: () => TailRec[A]) extends TailRec[A]
  private case class FlatMap[A0, B]( a0: TailRec[A0], cont0: A0 => TailRec[B]) extends TailRec[B]
  {
    // walk-around for existential type erasure
    type A = A0
    val a: TailRec[A] = a0
    val cont: A => TailRec[B] = cont0
  }

  ////////////////////////////////////////////////////////////////////////////////
  @tailrec
  def run[A](tailrec: TailRec[A]): A = tailrec.resume match {
    case Left(t)  => run(t)
    case Right(a) => a
  }

  ////////////////////////////////////////////////////////////////////////////////
  trait TrampolineInstances {
    implicit val trampolineMonad = new Monad[TailRec] {
      override def pure[A](a: => A): TailRec[A] = Pure(a)
      override def flatMap[A, B](fa: TailRec[A])(f: A => TailRec[B]): TailRec[B] = fa.flatMap(f)
    }
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////
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
