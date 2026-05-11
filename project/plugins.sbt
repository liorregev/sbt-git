
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

resolvers ++= Seq(
  "Sonatype Snapshots Nexus" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Nexus" at "https://s01.oss.sonatype.org/content/repositories/releases"
)

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.0")
addSbtPlugin("org.latestbit" % "sbt-gcs-plugin" % "1.17.0")