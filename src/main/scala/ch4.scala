object ch4 {

  sealed trait Option[+A]
  case object None extends Option[Nothing]
  case class Some[+A]( get: A) extends Option[A]

  object Option {

    implicit class OptionWrapper[A]( oa: Option[A]) {
      def map[B](f: A => B): Option[B] = Option.map(oa)(f)
      def flatMap[B](f: A => Option[B]): Option[B] = Option.flatMap(oa)(f)
      def getOrElse(alt: => A): A = Option.getOrElse(oa)(alt)
      def orElse(alt: => Option[A]): Option[A] = Option.orElse(oa)(alt)
      def filter(p: A => Boolean): Option[A] = Option.filter(oa)(p)
    }

    // listing 4.2
    // exercise 4.1
    def apply[A](a: A): Option[A] = Some(a)

    def map[A,B](oa: Option[A])(f: A => B): Option[B] =
      flatMap(oa)(a => Option(f(a)))

    def flatMap[A,B](oa: Option[A])(f: A => Option[B]): Option[B] =
      oa match {
        case Some(a) => f(a)
        case _ => None
      }

    def getOrElse[A](oa: Option[A])(alternative: => A): A =
      oa match {
        case Some(a) => a
        case _ => alternative
      }

    def orElse[A](oa: Option[A])(alt: => Option[A]): Option[A] =
      map(oa)(Option(_)).getOrElse( alt)

    def filter[A](oa: Option[A])(p: A => Boolean): Option[A] = {
      flatMap(oa)(a => if(p(a)) Option(a) else None)
    }

    // exercise 4.2
    def variance(xs: Seq[Double]): Option[Double] = {

      def mean( ds: Seq[Double]): Option[Double] =
        if(xs.isEmpty) None else Some(ds.sum / ds.size)

      mean(xs).flatMap(m => mean(xs.map(x => math.pow(x - m, 2))) )
    }

    // listing 4.3.2
    def lift[A,B](f: A => B): Option[A] => Option[B] =
      _.map(f)

    def may[A](a: => A): Option[A] =
      try{ Some(a) }
      catch { case e: Exception => None }

    def maybe[A,B](f: A => B): A => Option[B] =
      a => may(f(a))

    def ap[A,B](oa: Option[A])(f: Option[A => B]): Option[B] =
      flatMap(oa)(a => map(f)(_(a)))

    // exercise 4.3
    def map2[A,B,C](oa: Option[A], ob: Option[B])(f: (A, B) => C): Option[C] =
      oa.flatMap(a => ob.map(b => f(a,b)))

    // exercise 4.4
    def sequence[A](os: List[Option[A]]): Option[List[A]] =
      os.foldRight(Some(Nil): Option[List[A]])( Option.map2(_, _)( _ +: _) )  // think about Empty-List

    // exercise 4.5
    def traverse[A,B](a: List[A])(f: A => Option[B]): Option[List[B]] =
      a.foldRight(Some(Nil):Option[List[B]])((a, b) => Option.map2( f(a), b)( _ +: _))

    def sequenceWithTraverse[A](os: List[Option[A]]): Option[List[A]] =
      traverse(os)(identity )

  }

  // listing 4.4
  sealed trait Either[+E, +A] {

    // exercise 4.6
    def flatMap[B, EE>:E](f: A => Either[EE, B]): Either[EE,B] =
      this match {
        case Left(e) => Left(e)
        case Right(a) => f(a)
      }

    def getOrElse[B>:A](alt: => B): B =
      this match {
        case Left(_) => alt
        case Right(a) => a
      }

    def map[B](f: A => B): Either[E,B] =
      this.flatMap( a => Right(f(a)))

    def orElse[B>:A, EE>:E](alt: => Either[EE, B]): Either[EE,B] =
      this.map( a => Right(a): Either[EE,A]).getOrElse( alt)

    def map2[B,C, EE>:E](eb: Either[EE, B])(f: (A,B) => C): Either[EE,C] =
      this.flatMap( a => eb.map( b => f(a,b)))
  }

  object Either {

    // listing 4.4
    def may[A](a : => A): Either[Exception, A] =
      try { Right(a) } catch { case e: Exception => Left(e) }

    def maybe[A,B](f: A => B): A => Either[Exception,B] =
      a => may(f(a))

    // exercise 4.7
    def traverse[E,A,B](as: List[A])(f: A => Either[E, B]): Either[E, List[B]] =
      as.foldRight(Right(Nil): Either[E, List[B]])( (a, b) => f(a).map2(b)( _ +: _))

    def sequence[E,A](es: List[Either[E,A]]): Either[E, List[A]] =
      traverse(es)(identity)
  }

  case class Right[+A]( value: A) extends Either[Nothing, A]
  case class Left[+E]( value: E) extends Either[E, Nothing]

  // exercise 4.8
  sealed trait Done[+E, +A] {

    def flatMap[B, EE>:E](f: A => Done[EE,B]) : Done[EE,B] =
      this match {
        case Success(a) => f(a)
        case Errors(e) => Errors(e)
      }
    def map[B](f: A => B): Done[E,B] =
      this.flatMap( a => Success(f(a)) )
    def map2[B,C,EE>:E](eb: Done[EE,B])(f: (A,B) => C): Done[EE,C] =
      (this, eb) match {
        case (Success(a), Success(b)) => Success( f(a,b))
        case (Errors(e1), Errors(e2)) => Errors( e1 ++ e2)
        case (e @Errors(_), _) => e
        case (_, e @Errors(_)) => e
      }

    def ap[B, EE>:E](df: Done[EE, A => B]): Done[EE, B] =
      this.map2(df)( (a, f) => f(a))
  }

  case class Errors[+E]( get: Seq[E]) extends Done[E, Nothing]
  case class Success[+A]( get: A) extends Done[Nothing, A]

  object Done {
    def may[A]( a: => A) : Done[Exception, A] =
      try{ Success(a) } catch { case e: Exception => Errors(Seq(e))}

    def maybe[A,B](f: A => B): A => Done[Exception,B] =
      a => may(f(a))

    def traverse[E,A,B](as: List[A])(f: A => Done[E,B]): Done[E, List[B]] =
      as.foldRight(Success(Nil): Done[E, List[B]])( (a,b) =>f(a).map2(b)( _ +: _) )

    def sequence[E,A](ds: List[Done[E,A]]): Done[E, List[A]] =
      traverse(ds)( identity)
  }


}
