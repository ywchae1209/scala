package com.g3nie

object somethingToString {

  sealed trait Tree[A]
  case class Leaf[A]( value: A) extends Tree[A]
  case class Branch[A]( left: Tree[A], right: Tree[A]) extends Tree[A]

  ////////////////////////////////////////////////////////////////
  object Tree {

    // problem in here :: repetition cause performance O(N^2)
    def show[A](t: Tree[A]): String = t match {
      case Leaf (a) => a.toString
      case Branch (l, r) => "<" + show(l) + "|" + show(r) + ">"
    }

    ///////////////////////////////////////////////////////////////////
    // inspired by "A Gentle Introduction to Haskell" p.38
    // https://www.haskell.org/tutorial/haskell-98-tutorial.pdf
    // show vs shows
    ///////////////////////////////////////////////////////////////////
    // Linear time :: performance O(N)
    type Shows = String => String

    val `<` = (s: String) => "<" + s
    val `>` = (s: String) => ">" + s
    val `|` = (s: String) => "|" + s

    implicit class FunctionCompose[B, C](f: B => C ) {
      def |@| [A](g: A => B): A => C = f compose g
    }

    def shows[A](t: Tree[A]): Shows = t match {
      case Leaf (a) => s => (a.toString + s)
      case Branch (l, r) => `<` |@| shows(l) |@| `|` |@| shows(r) |@| `>`
    }
    def show2[A](t: Tree[A]): String = shows(t)("")
  }


  def main(args: Array[String]): Unit = {

    val sample:Tree[Int] = Branch( Branch( Leaf(1), Branch(Leaf(2), Leaf(3)) ), Leaf(4) )

    println( Tree.show2(sample))
    println( Tree.show(sample))
  }

}
