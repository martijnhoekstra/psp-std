package psp
package std

import api._

/** Indexed is somewhere between Each and Direct.
 *  There's an apply(index) method, but its size may not be known and may be infinite.
 *  We can memoize an Each into an Indexed.
 */
object Indexed {
  def indices: Indexed[Index]              = Pure(identity)
  def from(start: BigInt): Indexed[BigInt] = Pure(i => start + i.get)
  def from(start: Int): Indexed[Int]       = Pure(i => start + i.getInt)
  def from(start: Long): Indexed[Long]     = Pure(i => start + i.get)

  final case class Pure[A](f: Index => A) extends AnyVal with Indexed[A] {
    def size                                = impl.Size.Unknown
    def isEmpty                             = false
    def elemAt(i: Index): A                 = f(i)
    @inline def foreach(f: A => Unit): Unit = {
      var current: Long = 0L
      while (true) { f(elemAt(Index(current))) ; current += 1 }
    }
  }

  final class MemoIterator[+A](memo: Memo[A]) extends scIterator[A] {
    @volatile private[this] var index: Index = Index(0)
    def hasNext = memo isDefinedAt index
    def next: A = try memo(index) finally index += 1
  }

  final class Memo[+A](xs: Each[A]) extends Indexed[A] {
    @volatile private[this] var doneProducing = false
    @volatile private[this] var doneConsuming = false
    @volatile private[this] var memo = sciVector[A]()
    private[this] val handoff = new LinkedBlockingQueue[A](1)

    private[this] lazy val thread: Unit = spawn({ xs foreach handoff.put ; doneProducing = true })
    private[this] def seen = Precise(memo.length)
    private[this] def next(): A = handoff.poll match {
      case null => nullAs[A]
      case elem => elem doto (x => memo = memo :+ x)
    }
    private[this] def hasNext: Boolean = !doneConsuming && {
      handoff.peek match {
        case null if !doneProducing => threadYield() ; hasNext
        case null                   => doneConsuming = true ; false
        case _                      => true
      }
    }
    private[this] def advanceTo(index: Index): A = {
      if (index.isUndefined) abort(s"NoIndex")

      thread
      while (index.sizeIncluding > seen && hasNext) {
        next()
      }
      memo(index.getInt)
    }
    def isDefinedAt(i: Index): Boolean = {
      !i.isUndefined && (
           (seen containsIndex i)
        || (Try(andTrue(advanceTo(i))) | false)
      )
    }

    def iterator: scIterator[A]     = new MemoIterator(this)
    def foreach(f: A => Unit): Unit = iterator foreach f
    def apply(index: Index): A      = advanceTo(index)
    def elemAt(index: Index): A     = advanceTo(index)
    def size: Size                  = if (doneConsuming) seen else seen.atLeast
  }
}
