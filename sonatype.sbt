ThisBuild / organization := "com.gu.etag-caching"

ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/guardian/etag-caching"),
  "scm:git:git@github.com:guardian/etag-caching.git"
))

ThisBuild / pomExtra := (
  <url>https://github.com/guardian/etag-caching</url>
    <developers>
      <developer>
        <id>rtyley</id>
        <name>Roberto Tyley</name>
        <url>https://github.com/rtyley</url>
      </developer>
    </developers>
  )