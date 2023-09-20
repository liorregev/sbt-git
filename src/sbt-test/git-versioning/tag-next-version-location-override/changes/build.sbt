enablePlugins(GitVersioning)

git.baseVersion := "0.1"
git.versionProperty := "DUMMY_BUILD_VERSION"
git.useGitDescribe := true
git.gitMergeMessagePatterns := Seq(
  raw"Merge branch '(.*?)'"
)
git.baseLocation := "mydir"

val checkNoTag = taskKey[Unit]("checks the next patch version")
checkNoTag := {
  val v = version.value
  assert(v.startsWith("1.0.0"), s"tagged version should start with 1.0.0 ($v)")
}

val checkTaggedFix = taskKey[Unit]("checks the next patch version")
checkTaggedFix := {
  val v = version.value
  assert(v == "1.0.1", s"tagged version should be 1.0.1 not $v")
}