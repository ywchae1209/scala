import ch6.State.{modify, sequence, unit}

object ch6 {

  //////////////////////////////////////////////
  // class version of State Monad
  //////////////////////////////////////////////
  case class State[S, +A](apply: S => (A, S)) {

    def flatMap[B](f: A => State[S, B]): State[S, B] = State(s0 => {
      val (a, s1) = apply(s0)
      f(a).apply(s1)
    })

    def map[B](f: A => B): State[S, B] =
      flatMap(a => unit(f(a)))

    def map2[B, C](sb: State[S, B])(f: (A, B) => C): State[S, C] =
      flatMap(a => sb.map(b => f(a, b)))

    def ap[B](sf: State[S, A => B]): State[S, B] =
      flatMap(a => sf.map(f => f(a)))

    def zip[B](sb: State[S, B]): State[S, (A, B)] =
      map2(sb)(_ -> _)

    ////////////////////////////////////////////
    def map_2[B](f: A => B): State[S, B] = State(s0 => {
      val (a, s) = apply(s0)
      f(a) -> s
    })

    def map2_2[B, C](sb: State[S, B])(f: (A, B) => C): State[S, C] = State(s0 => {
      val (a, s1) = apply(s0)
      val (b, s2) = sb.apply(s1)

      f(a, b) -> s2
    })

    def ap_2[B](f0: State[S, A => B]): State[S, B] = State(s0 => {
      val (a, s1) = apply(s0)
      val (f, s2) = f0.apply(s1)

      f(a) -> s2
    })
  }

  object State {

    def unit[S, A](a: A): State[S, A] = State(s0 => (a, s0))

    def get[S]: State[S, S] = State(s => (s ,s))
    def set[S](s: S): State[S, Unit] = State(_ => (() ,s))

    def modify[S](f: S => S): State[S, Unit] =
      get.flatMap( s => set(f(s)) )

    def sequence[S, A](as: List[State[S, A]]): State[S, List[A]] =
      traverse(as)(identity)

    def traverse[S, A, B](as: List[A])(f: A => State[S, B]): State[S, List[B]] =
      as.foldRight(unit[S, List[B]](Nil))((a, b) => f(a).map2(b)(_ +: _))
  }

  //////////////////////////////////////////////
  trait RNG {
    def nextInt: (Int, RNG)
  }

  object RNG {
    case class Simple(seed: Long) extends RNG {
      override def nextInt: (Int, RNG) = {
        val newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
        val nextRNG = Simple(newSeed)
        val n = (newSeed >>> 16).toInt

        (n, nextRNG)
      }
    }

    def int: State[RNG, Int] = State(s0 => s0.nextInt)

    def nonNegative: State[RNG, Int] = int.map(math.abs)

    def double: State[RNG, Double] = int.map(_ / (Int.MaxValue.toDouble + 1))

    def ints(n: Int): State[RNG, List[Int]] = sequence(List.fill(n)(int))

    def boolean: State[RNG, Boolean] = int.map(_ % 2 == 0)

    def intDouble: State[RNG, (Int, Double)] = int.map2(double)(_ -> _)

    def double3: State[RNG, (Double, Double, Double)] = sequence(List.fill(3)(double)).map(ds => (ds(0), ds(1), ds(2)))

  }

  object Machine {
    sealed trait Input
    case object Coin extends Input
    case object Turn extends Input

    case class CandyMachine(locked: Boolean, candies: Int, coins: Int)

    def update: Input => CandyMachine => CandyMachine = (i: Input) => (s: CandyMachine) =>
      (i, s) match {
        case (_, CandyMachine(_, 0, _)) => s
        case (Coin, CandyMachine(false, _, _)) => s
        case (Turn, CandyMachine(true, _, _)) => s
        case (Coin, CandyMachine(true, candy, coin)) => CandyMachine(false, candy, coin + 1)
        case (Turn, CandyMachine(false, candy, coin)) => CandyMachine(true, candy - 1, coin)
      }

    def simulate(is: List[Input]): State[CandyMachine, (Int, Int)] = {

      sequence( is.map ( (modify[CandyMachine] _ ) compose update))
        .flatMap( _ => State.get.map(s => (s.coins, s.candies) )
        )
    }

  }
}
