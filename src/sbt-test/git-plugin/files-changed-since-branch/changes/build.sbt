def proj(name: String) = Project(name, file(name)).enablePlugins(GitVersioning)


lazy val a = proj("a")
lazy val b = proj("b")

enablePlugins(GitVersioning)

git.baseVersion := "1.0"
git.versionProperty := "DUMMY_BUILD_VERSION"
git.gitBaseBranch := Some("development")

val checkChangedFiles = taskKey[Unit]("checks the files changed in the last commit")
checkChangedFiles := {
  val lastCommit = git.gitFilesChangedLastCommit.value
  assert(lastCommit sameElements Seq("README3.md"), s"changed files should return 1 entries, got $lastCommit")
  val sinceBase = git.gitFilesChangedSinceBase.value
  assert(sinceBase sameElements Seq("README2.md", "README3.md"), s"changed files should return 2 entries, got $sinceBase")
}