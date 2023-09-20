enablePlugins(GitVersioning)

git.baseVersion := "0.1"
git.versionProperty := "DUMMY_BUILD_VERSION"
git.useGitDescribe := true
git.gitMergeMessagePatterns := Seq(
  raw"Merge branch '(.*?)'"
)

val checkTaggedFix = taskKey[Unit]("checks the next patch version")
checkTaggedFix := {
  val v = version.value
  assert(v == "1.0.1", s"tagged version should be 1.0.1 not $v")
}

val checkTaggedFeature = taskKey[Unit]("checks the next minor version")
checkTaggedFeature := {
  val v = version.value
  assert(v == "1.1.0", s"tagged version should be 1.1.0 not $v")
}

val checkTaggedMajor = taskKey[Unit]("checks the next major version")
checkTaggedMajor := {
  val v = version.value
  assert(v == "2.0.0", s"tagged version should be 2.0.0 not $v")
}