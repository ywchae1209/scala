object ch1 {

  case class Card()
  case class Coffee()
  case class Charge( cc: Card, n: Int) {
    def combine(o: Charge) : Charge = {
      if( o.cc == this.cc)
        Charge(this.cc, n + o.n)
      else
        throw new Exception( "can't combine : different Credit Card")
    }
  }

  object Coffee {
    def apply() : Coffee = new Coffee()
  }

  class Cafe {

    def buyCoffees( cc: Card, n: Int = 1): (List[Coffee], Charge) =
      List.fill(n)(Coffee()) -> Charge(cc, n)

    def buyCoffee(cc: Card): (Coffee, Charge) =
      Coffee() -> Charge(cc, 1)

    def coalesce( charges: List[Charge]): List[Charge] =
      charges
        .groupBy( _.cc)
        .values
        .map( _.reduce( _ combine _) )
        .toList
  }

}
