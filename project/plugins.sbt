
libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

resolvers ++= Seq(
  "Sonatype Snapshots Nexus" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Nexus" at "https://s01.oss.sonatype.org/content/repositories/releases"
)

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("io.github.liorregev" % "sbt-vault" % "0.5.0")