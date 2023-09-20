enablePlugins(GitVersioning)

git.baseVersion := "0.1"
git.versionProperty := "DUMMY_BUILD_VERSION"
git.useGitDescribe := true

val checkNextPatch = taskKey[Unit]("checks the next patch version")
checkNextPatch := {
  val nextPatch = git.nextPatchVersion.value
  assert(nextPatch.contains("1.0.1"), s"next patch should be: 1.0.1 not ${nextPatch}")
}

val checkNextMinor = taskKey[Unit]("checks the next minor version")
checkNextMinor := {
  val nextMinor = git.nextMinorVersion.value
  assert(nextMinor.contains("1.1.0"), s"next patch should be: 1.1.0 not ${nextMinor}")
}

val checkNextMajor = taskKey[Unit]("checks the next major version")
checkNextMajor := {
  val nextMajor = git.nextMajorVersion.value
  assert(nextMajor.contains("2.0.0"), s"next patch should be: 2.0.0 not ${nextMajor}")
}