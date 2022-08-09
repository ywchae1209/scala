
object typeClassExercise {

  ////////////////////////////////////////////////////////////////////////////////
  // 1. type-class ::  has common interface over type-parameter
  trait StringParser[T] { def parse(s: String) : T }

  ////////////////////////////////////////////////////////////////////////////////
  // 2. type instances :: has specific impl. per type.
  object StringParser {
    implicit object ParseSt extends StringParser[String] {
      override def parse(s: String): String = s
    }
    implicit object ParseBoolean extends StringParser[Boolean] {
      override def parse(s: String): Boolean = s.toBoolean
    }
    implicit object ParseDouble extends StringParser[Double] {
      override def parse(s: String): Double = s.toDouble
    }
    implicit object ParseInt extends StringParser[Int] {
      override def parse(s: String): Int = s.toInt
    }

    ////////////////////////////////////////////////////////////////////////////////
    // syntax note)
    //
    //    [T: StringParser] ....
    //    val p = implicitly[StringParser[T]]
    //
    //    is equal to
    //
    //    [T](implicit p: StringParser[T]kj
    //
    ////////////////////////////////////////////////////////////////////////////////

    // 2.1 interface syntax :: type-instance is created dynamically.
    implicit def ParseSeq[T: StringParser]: StringParser[Seq[T]] =
      (s: String) => {
        val p = implicitly[StringParser[T]]

        println(s"\tSeq of $s")
        s.split(",").toSeq.map(p.parse)
      }

    implicit def parseTuple[T, V](implicit p1: StringParser[T], p2 : StringParser[V])
    : StringParser[(T,V)] =
      s => {
//        val Array( lhs, rhs) = s.split("=")

        val n = s.indexOf("=")
        val lhs = s.take(n)
        val rhs = s.drop(n+1)

        println( s"\tlhs: $lhs, rhs: $rhs")
        p1.parse(lhs) -> p2.parse(rhs)
      }
  }

  ////////////////////////////////////////////////////////////////////////////////
  // 3. type-class interface is used by API-caller

  def parseString[T: StringParser](s: String): T = implicitly[StringParser[T]].parse(s)
  def parseString2[T](s: String)(implicit p:StringParser[T]): T = p.parse(s)

  def main(args: Array[String]): Unit = {
    println { parseString[Boolean]("true") }
    println { parseString[Seq[Boolean]]("true,false,true") }
    println { parseString[(String, Int)]("x=123") }
    println { parseString[(String, Seq[Int])]("x=123,1,2,3,4,5") }
    println { parseString[(Seq[String], Seq[Int])]("x,y,z=123,1,2,3,4,5") }
    println { parseString[Seq[(String, Int)]]("x=1,y=2,z=3") }

    println { parseString[(String, (String, Int))]("x=y=123") }

//    println { parseString[Seq[(Seq[String], Seq[Int])]]("a,b=1,2,c=2,d,e=3,4") }

    /* output
      true
        Seq of true,false,true
      ArraySeq(true, false, true)
        lhs: x, rhs: 123
      (x,123)
        lhs: x, rhs: 123,1,2,3,4,5
        Seq of 123,1,2,3,4,5
      (x,ArraySeq(123, 1, 2, 3, 4, 5))
        lhs: x,y,z, rhs: 123,1,2,3,4,5
        Seq of x,y,z
        Seq of 123,1,2,3,4,5
      (ArraySeq(x, y, z),ArraySeq(123, 1, 2, 3, 4, 5))
        Seq of x=1,y=2,z=3
        lhs: x, rhs: 1
        lhs: y, rhs: 2
        lhs: z, rhs: 3
      ArraySeq((x,1), (y,2), (z,3))
        lhs: x, rhs: y=123
        lhs: y, rhs: 123
      (x,(y,123))
     */

  }

}
