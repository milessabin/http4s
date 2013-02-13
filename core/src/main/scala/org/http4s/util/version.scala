//package org.http4s.util
//
//
//package object version {
//
//  sealed abstract class Modifier(val name: String, val isUpwards: Boolean = false, val order: Int = 99) {
//    val uppercaseName = name.toUpperCase
//
//    override def hashCode(): Int = uppercaseName.##
//
//    override def equals(obj: Any): Boolean = obj match {
//      case m: Modifier => m.uppercaseName == uppercaseName
//      case _ => false
//    }
//
//    override def toString: String = s"${name}(upwards: $isUpwards, order: $order)"
//  }
//
//  object Snapshot extends Modifier("SNAPSHOT", order = 30)
//  class Milestone(value: Int, prefix: String = "M") extends Modifier(s"${prefix}$value", order = 25)
//  class Custom(name: String) extends Modifier(name, isUpwards = true, order = 15)
//
//  object Version {
//    def apply(versionString: String): Version = {
//      val nrs = versionString.split("\\.")
//      require(nrs.nonEmpty, "There has to be at least 1 number in the version string")
//      val maj = nrs.head.toInt
//      if (nrs.tail.nonEmpty) {
//        val min = nrs.tail.head.toInt
//        if (nrs.tail.tail.nonEmpty) {
//          val fp = nrs.tail.tail.head
//          if (fp.indexOf('-') > -1) {
//            val parts = fp.split("-|_")
//            val pat = parts.head
//            val modif = parts.tail map {
//              case "SNAPSHOT" | "snapshot" => Snapshot
//              case m if m.startsWith("M") | m.startsWith("m") => new Milestone(0, "M")
//              case m => new Custom(m)
//            }
//            Version(maj, min, pat.toInt, modif.toSet)
//          } else Version(maj, min, fp.toInt)
//        } else {
//          Version(maj, min)
//        }
//      } else Version(maj)
//    }
//  }
//  case class Version(major: Int, minor: Int = 0, patch: Int = 0, modifiers: Set[Modifier] = Set.empty) extends Ordered[Version] {
//    def compare(that: Version): Int = {
//      val maj = major compare that.major
//      if (maj != 0) maj
//      else {
//        val min = minor compare that.minor
//        if (min != 0) min
//        else {
//          val pa = patch compare that.patch
//          if (pa != 0 || modifiers.isEmpty) pa
//          else {
//            val left = modifiers.sortBy(_.order).headOption
//            val right = that.modifiers.sortBy(_.order).headOption
//            if (sorted.nonEmpty) {
//              val lo = left.map(_.order) getOrElse Int.MaxValue
//              val ro = right.map(_.order) getOrElse Int.MaxValue
//              val cmp = -(lo compare ro)
//              if (cmp > 0) {
//                if (left.get.isUpwards) cmp else -cmp
//              } else if (cmp < 0) {
//                if (right.get.isUpwards) cmp else -cmp
//              } else cmp
//            } else {
//              0
//            }
//          }
//        }
//      }
//    }
//
//    private[this] def modifiersString = {
//      val names = modifiers.toList.sortBy(_.order).map(_.name)
//      if (names.nonEmpty)
//        s"-${modifiers.toList.sortBy(_.order).map(_.name).mkString("-")}"
//      else ""
//    }
//
//    override def toString: String = {
//
//      s"$major.$minor.${patch}$modifiersString"
//    }
//  }
//}