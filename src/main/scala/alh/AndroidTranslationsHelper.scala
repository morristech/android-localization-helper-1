package alh

import java.io.File
import scala.language.postfixOps
import scala.xml._

/** Helps to find missing or obsolete translations of localized resources
 *  in android project. */
object AndroidTranslationsHelper {
  def main(args: Array[String]) {
    if (args.isEmpty) {
      exit(helpMsg)
    }

    /** Android project root folder. */
    val project = new File(args(0))
    if (!project.exists) {
      exit("folder not exists")
    }

    /** Name of strings.xml */
    val stringsXmlFilename = if (args.length > 1) args(1) else "strings.xml"

    /** Try to find original strings.xml file in 'values' folder. */
    val stringsOrig = findStrings(project, "values$", stringsXmlFilename)
    if (stringsOrig.isEmpty) {
      exit("res/values/"+stringsXmlFilename+" not found")
    }

    /** Try to find localized strings.xml file in 'values*' folders. */
    val stringsLocalized =
      findStrings(project, "values.", stringsXmlFilename) sortWith { _.getParent < _.getParent }
    if (stringsLocalized.isEmpty) {
      exit("localized resources not found")
    }

    /** Original XML resources */
    val rOrig: NodeSeq = XML.loadFile(stringsOrig.head) \\ "resources"

    stringsLocalized foreach { f =>
      val r = XML.loadFile(f) \\ "resources"
      printNiceName(f)
      val extraLine_? =
        resTypes map { t =>
          val names = mkNamesSet(r, t)
          val namesOrig = mkNamesSet(rOrig, t)
          printDiffs(t, namesOrig diff names, names diff namesOrig)
        } contains true
      if (extraLine_?) println
    }
  }

  /** Returns array of files according to
   *  'dir'/res/'regex'/'filename' pattern. */
  def findStrings(dir: File, regex: String, filename: String): Array[File] =
    dir.listFiles
      .withFilter { _.getName == "res" }
      .flatMap { _.listFiles.filter(f => regex.r.findFirstIn(f.getName).isDefined) }
      .flatMap { _.listFiles.filter(_.getName == filename) }

  /** Returns a set of resource names. */
  def mkNamesSet(ns: NodeSeq, tag: String): Set[String] =
    (ns \ tag) map { _ \ "@name" text } toSet

  def printNiceName(f: File) {
    println(f.getParentFile.getName + "/" + f.getName)
  }

  /**
   * Print diff section for given resource type.
   *
   *  @return flag, if there is a need for extra blank line after output */
  def printDiffs(resType: String, t: Set[String], o: Set[String]): Boolean = {
    if (t.nonEmpty || o.nonEmpty) {
      println(" " + resType)
      t map { "  [T] " + _ } foreach println
      o map { "  [O] " + _ } foreach println
      true
    } else false
  }

  def exit(msg: String) {
    println(msg)
    sys.exit(0)
  }

  /** List of android resource types. */
  val resTypes = "string" :: "string-array" :: "plurals" :: Nil

  val helpMsg =
    """|Helps to find missing or obsolete translations for android resources
       |
       |Usage: `alh.sh /path/to/android/project [arrays.xml]'
       |You must specify android project folder.
       |Second parameter is optional, it specifies resources filename (strings.xml by default).
       |
       |Output:
       |[filename]
       | *[resource type]
       |  *[T] [resource name]
       |  *[O] [resource name]
       |
       |[T] Resources need to be translated (exists in original xml but not in localized version)
       |[O] Obsolete resources (exists in localized version but not in original one)""".stripMargin
}
