import ReleaseTransformations.*

ThisBuild / scalaVersion := "2.13.11"
ThisBuild / crossScalaVersions := Seq(scalaVersion.value, "3.3.0")
ThisBuild / scalacOptions := Seq("-deprecation", "-release","11")
ThisBuild / licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))


lazy val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.16" % Test
  ),
  Test / testOptions +=
    Tests.Argument(TestFrameworks.ScalaTest, "-u", s"test-results/scala-${scalaVersion.value}", "-o")
)

lazy val core =
  project.settings(baseSettings,
    libraryDependencies ++= Seq(
      "com.github.blemale" %% "scaffeine" % "5.2.1"
    )
  )

def awsS3WithSdkVersion(version: Int)=
  Project(s"aws-s3-sdk-v$version", file(s"aws-s3/aws-sdk-v$version"))
    .dependsOn(`aws-s3-base`)
    .settings(baseSettings,
      libraryDependencies ++= Seq(
        awsSdkForVersion(version),
        "com.adobe.testing" % "s3mock-testcontainers" % "3.0.1" % Test
      ),
      Compile / doc / sources := // https://github.com/lampepfl/dotty/issues/15288, see also https://github.com/scanamo/scanamo/pull/1643
        { if (scalaVersion.value.startsWith("3.")) Nil else (Compile / doc / sources).value }
    )

val awsSdkForVersion = Map(
//  1 -> "com.amazonaws" % "aws-java-sdk-s3" % "1.12.487",
  2 -> "software.amazon.awssdk" % "s3" % "2.20.84"
)

lazy val `aws-s3-base` =
  project.in(file("aws-s3/base")).dependsOn(core)


lazy val `aws-s3-sdk-v2` = awsS3WithSdkVersion(2)

lazy val `etag-caching-root` = (project in file("."))
  .aggregate(
    core,
    `aws-s3-base`,
    `aws-s3-sdk-v2`
  ).settings(baseSettings).settings(
    publishArtifact := false,
    publish := {},
    publishLocal := {},
    releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      // For non cross-build projects, use releaseStepCommand("publishSigned")
      releaseStepCommandAndRemaining("+publishSigned"),
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
