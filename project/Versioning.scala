package sbtversionpolicy.withsbtrelease

import sbtrelease.{ Version, versionFormatError }
import sbtversionpolicy.Compatibility
import sbtversionpolicy.SbtVersionPolicyPlugin.aggregatedAssessedCompatibilityWithLatestRelease

import sbt._
object ReleaseVersion {

  /**
   * @return a [release version function](https://github.com/sbt/sbt-release?tab=readme-ov-file#custom-versioning)
   *         that bumps the patch, minor, or major version number depending on the provided
   *         compatibility level.
   */
  def fromCompatibility(compatibility: Compatibility): String => String = {
    val maybeBump =
      compatibility match {
        case Compatibility.None => Some(Version.Bump.Major)
        case Compatibility.BinaryCompatible => Some(Version.Bump.Minor)
        case Compatibility.BinaryAndSourceCompatible => None // No need to bump the patch version, because it has already been bumped when sbt-release set the next release version
      }
    { (currentVersion: String) =>
      val versionWithoutQualifier =
        Version(currentVersion)
          .getOrElse(versionFormatError(currentVersion))
          .withoutQualifier
      (maybeBump match {
        case Some(bump) => versionWithoutQualifier.bump(bump)
        case None => versionWithoutQualifier
      }).string
    }
  }

  /**
   * Convenient task that returns a [release version function](https://github.com/sbt/sbt-release?tab=readme-ov-file#custom-versioning)
   * based on the assessed compatibility level of the project (ie, the highest level of compatibility
   * satisfied by all the sub-projects aggregated by this project).
   *
   * Use it in the root project of your `.sbt` build definition as follows:
   *
   * {{{
   *   import sbtversionpolicy.withsbtrelease.ReleaseVersion
   *
   *   val `my-project` =
   *     project
   *       .in(file("."))
   *       .aggregate(mySubproject1, mySubproject2)
   *       .settings(
   *         releaseVersion := ReleaseVersion.fromAggregatedAssessedCompatibility.value
   *       )
   * }}}
   */
  val fromAggregatedAssessedCompatibility =
    Def.task {
      val log = Keys.streams.value.log
      val compatibility = aggregatedAssessedCompatibilityWithLatestRelease.value
      log.debug(s"Aggregated compatibility level is ${compatibility}")
      fromCompatibility(compatibility)
    }

}
