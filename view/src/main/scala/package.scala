package psp

import scala.{ collection => sc }
import sc.{ mutable => scm, immutable => sci }
import psp.core.impl._

package core {
  abstract class PackageTraitsExceptShadowing extends PspUtility with PspTypes with PspHighPriority with PspUtilityMethods with JioCreation
  abstract class PackageTraits extends PackageTraitsExceptShadowing with PspShadowScala
}

package object core extends PackageTraits with psp.std.PackageLevel {
  // Temporary helpers to ease reconciliation with psp.std.
  type Show[A] = psp.std.Show[A]
  type Index   = psp.std.Index
  val Index = psp.std.Index
  val Show = psp.std.Show
  implicit def intToIndex(x: Int): Index = psp.std.Index(x)
  implicit def indexToInt(x: psp.std.Index): Int = x.intIndex

  def zeroSize    = Size.Zero
  def unknownSize = SizeInfo.Unknown

  implicit class TraversableToPsp[A](xs: GenTraversableOnce[A]) {
    def toPsp: Foreach[A] = Foreach traversable xs
  }

  implicit class JavaIteratorToPsp[A](xs: jIterator[A]) {
    def toScalaIterator = new scala.Iterator[A] {
      def next    = xs.next
      def hasNext = xs.hasNext
    }
    def toPsp: Foreach[A] = toScalaIterator.toTraversable.toPsp
  }

  implicit class JavaCollectionToPsp[A](xs: jAbstractCollection[A]) {
    def toPsp: Foreach[A] = toTraversable.toPsp
    def toTraversable: Traversable[A] = toScalaIterator.toTraversable
    def toScalaIterator = new scala.Iterator[A] {
      val it = xs.iterator
      def next    = it.next
      def hasNext = it.hasNext
    }
  }
}
