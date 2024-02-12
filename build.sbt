organization := "com.github.sbt"
name := "sbt-git"
licenses := Seq(("BSD-2-Clause", url("https://opensource.org/licenses/BSD-2-Clause")))
description := "An sbt plugin that offers git features directly inside sbt"
developers := List(Developer("jsuereth", "Josh Suereth", "joshua suereth gmail com", url("http://jsuereth.com/")))
startYear := Some(2011)
homepage := scmInfo.value map (_.browseUrl)
scmInfo := Some(ScmInfo(url("https://github.com/sbt/sbt-git"), "scm:git:git@github.com:sbt/sbt-git.git"))

lazy val scala212 = "2.12.21"
lazy val scala3 = "3.8.3"

crossScalaVersions := Seq(scala212, scala3)

enablePlugins(GitVersioning, SbtPlugin, VaultPlugin)
vault.vaultAddress := vault.VaultConnection("https://vault-prod.placer.team")
vault.credentialsKeys += vault.CredentialsKey("kv/services/jfrog/ci", "name", "api_key", "Artifactory Realm", "placer.jfrog.io")
vault.selectedLoginMethods := Seq(
  vault.loginMethods.GCPServiceAccount("gcp-sa-ro"),
  vault.loginMethods.None
)
git.baseVersion := "1.0"

libraryDependencies ++= Seq(
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.5.202508271544-r",
  "com.michaelpollmeier" % "versionsort" % "1.0.17",
  "org.apache.logging.log4j" % "log4j-api" % "2.17.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.17.1",
  "org.scalameta" %% "munit" % "0.7.29" % Test
)

(pluginCrossBuild / sbtVersion) := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.5.8"
    case _ => "2.0.0-RC11"
  }
}

scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      Seq(
        "-release:8"
      )
    case "3" =>
      Nil
  }
}

ThisBuild / versionScheme := Some("semver-spec")
scriptedLaunchOpts += s"-Dproject.version=${version.value}"
scriptedBufferLog := false
publishTo := Some("Artifactory Realm Releases" at "https://placer.jfrog.io/artifactory/placer-mvn-release-local")