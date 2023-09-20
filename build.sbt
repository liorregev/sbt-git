organization := "com.github.sbt"
sonatypeProfileName := "com.github.sbt"
name := "sbt-git"
licenses := Seq(("BSD-2-Clause", url("https://opensource.org/licenses/BSD-2-Clause")))
description := "An sbt plugin that offers git features directly inside sbt"
developers := List(Developer("jsuereth", "Josh Suereth", "joshua suereth gmail com", url("http://jsuereth.com/")))
startYear := Some(2011)
homepage := scmInfo.value map (_.browseUrl)
scmInfo := Some(ScmInfo(url("https://github.com/sbt/sbt-git"), "scm:git:git@github.com:sbt/sbt-git.git"))

crossSbtVersions := List("1.3.13")

enablePlugins(GitVersioning, SbtPlugin, VaultPlugin)
vault.vaultAddress := vault.VaultConnection("https://vault-prod.placer.team")
vault.credentialsKeys += vault.CredentialsKey("kv/services/jfrog/ci", "name", "api_key", "Artifactory Realm", "placer.jfrog.io")
vault.selectedLoginMethods := Seq(
  vault.loginMethods.GCPServiceAccount("gcp-sa-ro"),
  vault.loginMethods.None
)
git.baseVersion := "1.0"

libraryDependencies ++= Seq(
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.0.202109080827-r",
  "org.apache.logging.log4j" % "log4j-api" % "2.17.1",
  "org.apache.logging.log4j" % "log4j-core" % "2.17.1",
  "com.michaelpollmeier" % "versionsort" % "1.0.11",
  "org.scalameta" %% "munit" % "0.7.29" % Test
)

ThisBuild / versionScheme := Some("semver-spec")
scriptedLaunchOpts += s"-Dproject.version=${version.value}"
publishTo := Some("Artifactory Realm Releases" at "https://placer.jfrog.io/artifactory/placer-mvn-release-local")