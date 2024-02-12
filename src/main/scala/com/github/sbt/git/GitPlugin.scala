package com.github.sbt.git

import sbt.Keys.*
import sbt.{Def, *}

import scala.util.matching.Regex

/** This plugin has all the basic 'git' functionality for other plugins. */
//noinspection ScalaUnusedSymbol
object SbtGit {

  private object GitKeys {
    // Read-only git settings and values for use in other build settings.
    // Note: These are all grabbed using jgit currently.
    val gitReader = SettingKey[ReadableGit]("git-reader", "This gives us a read-only view of the git repository.")
    val gitBranch = SettingKey[Option[String]]("git-branch", "Target branch of a git operation")
    val gitCurrentBranch = SettingKey[String]("git-current-branch", "The current branch for this project.")
    val gitCurrentTags = SettingKey[Seq[String]]("git-current-tags", "The tags associated with this commit.")
    val gitHeadCommit = SettingKey[Option[String]]("git-head-commit", "The commit sha for the top commit of this project.")
    val gitHeadMessage = SettingKey[Option[String]]("git-head-message", "The message for the top commit of this project.")
    val gitHeadCommitDate = SettingKey[Option[String]]("git-head-commit-date", "The commit date for the top commit of this project in ISO-8601 format.")
    val gitDescribedVersion = SettingKey[Option[String]]("git-described-version", "Version as returned by `git describe --tags`.")
    val gitUncommittedChanges = SettingKey[Boolean]("git-uncommitted-changes", "Whether there are uncommitted changes.")
    val gitMergeMessagePatterns = settingKey[Seq[String]]("Collection of regex patterns with one sub-group to parse commit messages of merge commits")
    val gitMergeFrom = SettingKey[Option[String]]("git-merge-from", "Possible name of a branch HEAD is a merge from")
    val gitBaseBranch = SettingKey[Option[String]]("git-base-branch", "Name of the branch this branch is based on")
    val gitFilesChangedLastCommit = SettingKey[Seq[String]]("git-last-changes", "List of files changed in the last commit")
    val gitFilesChangedSinceBase = SettingKey[Seq[String]]("git-changes-since", "List of files changed in the last commit")
    val versionRegex = settingKey[Regex]("Regex pattern for parsin versions, should have 3 subgroups for major, minor and patch")
    val nextPatchVersion = taskKey[Option[String]]("Returns the next patch version")
    val nextMinorVersion = taskKey[Option[String]]("Returns the next minor version")
    val nextMajorVersion = taskKey[Option[String]]("Returns the next major version")
    val tagNextVersion = taskKey[String]("Tags the next version of the project")
    val createVersionTag = settingKey[Boolean]("Should SBT create version tags for this project, used when multiple projects share tags")
    val tagPrefix = settingKey[Option[String]]("Prefix for version tags in the project, used when multiple projects share tags")
    val baseLocation = settingKey[String]("Path to check for changed files in latest merge commit").withRank(KeyRanks.Invisible)

    // A Mechanism to run Git directly.
    val gitRunner = TaskKey[GitRunner]("git-runner", "The mechanism used to run git in the current build.")

    // Keys associated with setting a version number.
    val useGitDescribe = SettingKey[Boolean]("use-git-describe", "Get version by calling `git describe` on the repository")
    val gitDescribePatterns = SettingKey[Seq[String]]("git-describe-patterns", "Patterns to `--match` against when using `git describe`")
    val gitTagToVersionNumber = SettingKey[String => Option[String]]("git-tag-to-version-number", "Converts a git tag string to a version number.")

    // Component version strings.  We use these when determining the actual version.
    val formattedShaVersion = settingKey[Option[String]]("Completely formatted version string which will use the git SHA. Override this to change how the SHA version is formatted.")
    val formattedDateVersion = settingKey[String]("Completely formatted version string which does not rely on git.  Used as a fallback.")

    // Helper suffix/prefix information for generated default version strings.
    val baseVersion = SettingKey[String]("base-version", "The base version number which we will append the git version to.")
    val versionProperty = SettingKey[String]("version-property", "The system property that can be used to override the version number.  Defaults to `project.version`.")
    val uncommittedSignifier = SettingKey[Option[String]]("uncommitted-signifier", "Optional additional signifier to signify uncommitted changes")

    // The remote repository we're using.
    val gitRemoteRepo = SettingKey[String]("git-remote-repo", "The remote git repository associated with this project")

    // Git worktree workaround
    val useConsoleForROGit = SettingKey[Boolean]("console-ro-runner", "Whether to shell out to git for ro ops in the current build.")
  }

  private object GitCommand {
    import complete.*
    import complete.DefaultParsers.*

    private val action: (State, Seq[String]) => State = { (state, args) =>
      val extracted = Project.extract(state)
      val (state2, runner) = extracted.runTask(GitKeys.gitRunner, state)
      val dir = extracted.get(baseDirectory)
      runner(args: _*)(dir, state2.log)
      state2
    }

    // the git command we expose to the user
    val command: Command = Command("git")(s =>  fullCommand(s)){ (state, arg) =>
      val (command, args) = arg
      action(state, command +: args)
    }

    private val QuotedString: Parser[String] = DQuoteClass ~> any.+.string.filter(!_.contains(DQuoteClass), _ => "Invalid quoted string") <~ DQuoteClass

    // the parser providing auto-completion for git command
    // Note: This isn't an exact parser for git, it just tries to make it more convenient in sbt with a modicum of autocomplete.
    // Ideally we'd use the bash autocompletion scripts or zsh ones for full and complete information, but this actually
    // gives us a lot of bang for the buck.
    private def fullCommand(state: State): Parser[(String, Seq[String])] = {
      val extracted = Project.extract(state)
      val reader = extracted.get(GitKeys.gitReader)
      implicit val branches: Seq[String] = reader.withGit(_.branches) ++ reader.withGit(_.remoteBranches) :+ "HEAD"
      // let's not forget the user can define its own git commands and aliases so we don't want to parse the command
      // TODO we could though provide a list of available git commands
      // TODO some git commands like add take filepaths as arguments
      token(Space) ~> token(NotQuoted, "<command>") ~ (Space ~> token(branch | QuotedString)).*
    }

    private def branch(implicit branches: Seq[String]): Parser[String] = NotQuoted.examples(branches.toSet)

    private def isGitRepo(dir: File): Boolean = {
      if (System.getenv("GIT_DIR") != null) true
      else isGitDir(dir)
    }

    @scala.annotation.tailrec
    private def isGitDir(dir: File): Boolean = {
      if (dir.listFiles().map(_.getName).contains(".git")) true
      else {
        val parent = dir.getParentFile
        if (parent == null) false
        else isGitDir(parent)
      }
    }

    val prompt: State => String = { state =>
      val extracted = Project.extract(state)
      val reader = extracted get GitKeys.gitReader
      val dir = extracted get baseDirectory
      val name = extracted get Keys.name
      if (isGitRepo(dir)) {
        val branch = reader.withGit(_.branch)
        name + "(" + branch + ")> "
      } else {
        name + "> "
      }
    }
  }

  // Build settings.
  import GitKeys.*
  def buildSettings: Seq[Def.Setting[_]] = Seq(
    useConsoleForROGit := false,
    gitReader := new DefaultReadableGit(baseDirectory.value, if (useConsoleForROGit.value) Some(new ConsoleGitReadableOnly(ConsoleGitRunner, file("."), sLog.value)) else None),
    gitRunner := ConsoleGitRunner,
    gitBaseBranch := None,
    gitHeadCommit := gitReader.value.withGit(_.headCommitSha),
    gitHeadMessage := gitReader.value.withGit(_.headCommitMessage),
    gitHeadCommitDate := gitReader.value.withGit(_.headCommitDate),
    gitDescribePatterns := Seq.empty[String],
    gitCurrentTags := gitReader.value.withGit(_.currentTags),
    gitCurrentBranch := Option(gitReader.value.withGit(_.branch)).getOrElse(""),
    ThisBuild / gitUncommittedChanges := gitReader.value.withGit(_.hasUncommittedChanges),
    gitMergeMessagePatterns := Seq.empty[String],
    gitFilesChangedLastCommit := gitReader.value.withGit(_.changedFiles),
    gitFilesChangedSinceBase := gitReader.value.withGit(git => gitBaseBranch.value.map(git.changedFilesSince).getOrElse(Nil)),
    gitMergeFrom := {
      for {
        headMessage <- gitHeadMessage.value.map(_.trim)
        mergedFrom <- gitMergeMessagePatterns.value
          .map(_.r.unanchored)
          .flatMap { regex =>
            headMessage match {
              case regex(branch) => Option(branch)
              case _ => None
            }
          }
          .headOption
      } yield mergedFrom
    },
    scmInfo := parseScmInfo(gitReader.value.withGit(_.remoteOrigin))
  )
  private[sbt] def parseScmInfo(remoteOrigin: String): Option[ScmInfo] = {
    val user = """(?:[^@\/]+@)?"""
    val domain = """([^\/]+)"""
    val gitPath = """(.*?)(?:\.git)?\/?$"""
    val unauthenticated = raw"""(?:git|https?|ftps?)://$domain/$gitPath""".r
    val ssh = raw"""ssh://$user$domain/$gitPath""".r
    val headlessSSH = raw"""$user$domain:$gitPath""".r

    def buildScmInfo(domain: String, repo: String): Option[ScmInfo] = Option(
      ScmInfo(
        url(s"https://$domain/$repo"),
        s"scm:git:https://$domain/$repo.git",
        Some(s"scm:git:git@$domain:$repo.git")
      )
    )

    remoteOrigin match {
      case unauthenticated(domain, repo) => buildScmInfo(domain,repo)
      case ssh(domain, repo) => buildScmInfo(domain,repo)
      case headlessSSH(domain, repo) => buildScmInfo(domain,repo)
      case _ => None
    }
  }

  private lazy val fixRegex = raw"(hotfix|bugfix)/.*".r
  private lazy val featureRegex = raw"feature/.*".r
  private lazy val majorRegex = raw"major/.*".r

  val projectSettings: Seq[Def.Setting[_]] = Seq(
    // Input task to run git commands directly.
    commands += GitCommand.command,
    gitTagToVersionNumber := git.defaultTagByVersionStrategy(tagPrefix.value),
    gitDescribePatterns := tagPrefix.value.map(prefix => Seq(s"$prefix-*")).getOrElse(Seq.empty),
    gitDescribedVersion := gitReader.value.withGit(_.describedVersion((ThisProject / gitDescribePatterns).value)).map(v => git.gitTagToVersionNumber.value(v).getOrElse(v)),
    versionRegex := raw"^(\d+)\.(\d+)\.(\d+)-\d+-g[a-f\d]+(-SNAPSHOT)?".r,
    nextPatchVersion := {
      val regex = git.versionRegex.value
      version.value match {
        case regex(major, minor, patch, _) =>
          Option(s"$major.$minor.${patch.toInt + 1}")
        case _ => None
      }
    },
    nextMinorVersion := {
      val regex = git.versionRegex.value
      version.value match {
        case regex(major, minor, _, _) =>
          Option(s"$major.${minor.toInt + 1}.0")
        case _ => None
      }
    },
    nextMajorVersion := {
      val regex = git.versionRegex.value
      version.value match {
        case regex(major, _, _, _) =>
          Option(s"${major.toInt + 1}.0.0")
        case _ => None
      }
    },
    tagPrefix := None,
    createVersionTag := true,
    baseLocation := {
      val base = (ThisBuild / baseDirectory).value
      val thisProject = baseDirectory.value
      base.toPath.relativize(thisProject.toPath).toString
    },
    tagNextVersion := Def.taskDyn {
      val runner = git.runner.value
      val logger = streams.value.log
      val changedFiles = git.gitFilesChangedLastCommit.value
      val shouldCreateVersionTag = createVersionTag.value
      val projName = name.value
      Def.task {
        val location = git.baseLocation.value
        if(shouldCreateVersionTag) {
          logger.debug(s"$projName / shouldCreateVersionTag = true")
          if(changedFiles.exists(_.startsWith(location))) {
            logger.debug(s"Found changed files starting with $projName / location ($location)")
            val mergeFrom = git.gitMergeFrom.value
            mergeFrom
              .flatMap {
                case fixRegex(_) => nextPatchVersion.value
                case featureRegex() => nextMinorVersion.value
                case majorRegex() => nextMajorVersion.value
                case _ =>
                  logger.debug(s"Merge from mismatch: $mergeFrom")
                  None
              }
              .map(newVersion => {
                val tag = tagPrefix.value.map(prefix => s"$prefix-$newVersion").getOrElse(newVersion)
                logger.debug(s"New version for project $projName: $newVersion")
                runner("tag", "-a", tag, "-m", s"$projName version $newVersion")(file("."), logger)
                s"$projName = $newVersion"
              })
              .getOrElse("")
          } else {
            logger.debug(s"No changed files starting with $projName / location ($location)")
            ""
          }
        } else {
          logger.debug(s"$projName / shouldCreateVersionTag = false")
          ""
        }
      }
    }.value
  )

  /** A Predefined setting to use JGit runner for git. */
  def useJGit: Setting[_] = ThisBuild / gitRunner := JGitRunner

  /** Setting to use console git for readable ops, to allow working with git worktrees */
  def useReadableConsoleGit: Setting[_] = ThisBuild / useConsoleForROGit := true

  /** Adapts the project prompt to show the current project name *and* the current git branch. */
  def showCurrentGitBranch: Setting[_] =
    shellPrompt := GitCommand.prompt


  /** Uses git to control versioning.
   *
   * Versioning runs through the following:
   *
   * 1. Looks at version-property settings, and checks the sys.props to see if this has a value.
   * 2. Looks at the project tags.  The first to match the `gitTagToVersionNumberSetting` is used to assign the version.
   * 3. if we have a head commit, we attach this to the base version setting "<base-version>.<git commit sha>"
   * 4. We append the current timestamp to the base version: "<base-version>.<timestamp>"
   */
  def versionWithGit: Seq[Setting[_]] =
    Seq(
      ThisBuild / versionProperty := "project.version",
      ThisBuild / uncommittedSignifier := Some("SNAPSHOT"),
      ThisBuild / useGitDescribe := false,
      ThisBuild / formattedShaVersion := {
        val base = git.baseVersion.?.value
        val suffix =
          git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)
        git.gitHeadCommit.value map { sha =>
          git.defaultFormatShaVersion(base, sha, suffix)
        }
      },
      ThisBuild / formattedDateVersion := {
        val base = git.baseVersion.?.value
        git.defaultFormatDateVersion(base, new java.util.Date)
      },
      ThisBuild / isSnapshot := {
        git.gitCurrentTags.value.isEmpty || git.gitUncommittedChanges.value
      },
    )

  def versionProjectWithGit: Seq[Setting[_]] =
    Seq(
      ThisProject / useGitDescribe := false,
      ThisProject / version := {
        val overrideVersion =
          git.overrideVersion(git.versionProperty.value)
        val uncommittedSuffix =
          git.makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)
        val releaseVersion =
          git.releaseVersion(git.gitCurrentTags.value, (ThisProject / gitTagToVersionNumber).value, uncommittedSuffix)
        val describedVersion =
          git.flaggedOptional(git.useGitDescribe.value, git.describeVersion((ThisProject / gitDescribedVersion).value, uncommittedSuffix))
        val datedVersion = formattedDateVersion.value
        val commitVersion = formattedShaVersion.value
        //Now we fall through the potential version numbers...
        git.makeVersion(Seq(
          overrideVersion,
          releaseVersion,
          describedVersion,
          commitVersion
        )) getOrElse datedVersion // For when git isn't there at all.
      }
    )

  /** A holder of keys for simple config. */
  object git {
    val remoteRepo = GitKeys.gitRemoteRepo
    val branch = GitKeys.gitBranch
    val runner = ThisBuild / GitKeys.gitRunner
    val gitHeadCommit = ThisBuild / GitKeys.gitHeadCommit
    val gitHeadMessage = ThisBuild / GitKeys.gitHeadMessage
    val gitHeadCommitDate = ThisBuild / GitKeys.gitHeadCommitDate
    val useGitDescribe = ThisProject / GitKeys.useGitDescribe
    val gitDescribePatterns = ThisProject / GitKeys.gitDescribePatterns
    val gitDescribedVersion = ThisProject / GitKeys.gitDescribedVersion
    val gitCurrentTags = ThisBuild / GitKeys.gitCurrentTags
    val gitCurrentBranch = ThisBuild / GitKeys.gitCurrentBranch
    val gitTagToVersionNumber = ThisProject / GitKeys.gitTagToVersionNumber
    val versionRegex = ThisProject / GitKeys.versionRegex
    val nextPatchVersion = ThisProject / GitKeys.nextPatchVersion
    val nextMinorVersion = ThisProject / GitKeys.nextMinorVersion
    val nextMajorVersion = ThisProject / GitKeys.nextMajorVersion
    val baseLocation = ThisProject / GitKeys.baseLocation
    val tagNextVersion = ThisProject / GitKeys.tagNextVersion
    val createVersionTag = ThisProject / GitKeys.createVersionTag
    val tagPrefix = ThisProject / GitKeys.tagPrefix
    val baseVersion = ThisBuild / GitKeys.baseVersion
    val versionProperty = ThisBuild / GitKeys.versionProperty
    val gitUncommittedChanges = ThisBuild / GitKeys.gitUncommittedChanges
    val gitBaseBranch = ThisBuild / GitKeys.gitBaseBranch
    val gitFilesChangedLastCommit = ThisBuild / GitKeys.gitFilesChangedLastCommit
    val gitFilesChangedSinceBase = ThisBuild / GitKeys.gitFilesChangedSinceBase
    val gitMergeFrom = ThisBuild / GitKeys.gitMergeFrom
    val gitMergeMessagePatterns = ThisBuild / GitKeys.gitMergeMessagePatterns
    val uncommittedSignifier = ThisBuild / GitKeys.uncommittedSignifier
    val formattedShaVersion = ThisBuild / GitKeys.formattedShaVersion
    val formattedDateVersion = ThisBuild / GitKeys.formattedDateVersion


    def defaultTagByVersionStrategy(prefix: Option[String]): String => Option[String] = { tag =>
      prefix match {
        case Some(value) =>
          if (tag matches s"$value-[0-9].*") Some(tag.replace(s"$value-", ""))
          else None
        case None =>
          if (tag matches "v[0-9].*") Some(tag drop 1)
          else None
      }
    }

    def defaultFormatShaVersion(baseVersion: Option[String], sha:String, suffix: String):String = {
      baseVersion.map(_ +"-").getOrElse("") + sha + suffix
    }

    def defaultFormatDateVersion(baseVersion:Option[String], date:java.util.Date):String = {
      val df = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
      df setTimeZone java.util.TimeZone.getTimeZone("GMT")
      baseVersion.map(_ +"-").getOrElse("") + (df format new java.util.Date)
    }

    def flaggedOptional(flag: Boolean, value: Option[String]): Option[String] =
      if(flag) value
      else None

    def makeUncommittedSignifierSuffix(hasUncommittedChanges: Boolean, uncommittedSignifier: Option[String]): String =
      flaggedOptional(hasUncommittedChanges, uncommittedSignifier).map("-" + _).getOrElse("")

    def describeVersion(gitDescribedVersion: Option[String], suffix: String): Option[String] = {
      gitDescribedVersion.map(_ + suffix)
    }

    def releaseVersion(currentTags: Seq[String], releaseTagVersion: String => Option[String], suffix: String): Option[String] = {
      val versions =
        for {
          tag <- currentTags
          version <- releaseTagVersion(tag)
        } yield version

      // NOTE - Selecting the last tag or the first tag should be an option.
      val highestVersion = versions.sortWith { versionsort.VersionHelper.compare(_, _) > 0 }.headOption
      highestVersion.map(_ + suffix)
    }

    def overrideVersion(versionProperty: String): Option[String] = Option(sys.props(versionProperty))

    def makeVersion(versionPossibilities: Seq[Option[String]]): Option[String] = {
      versionPossibilities.reduce(_ orElse _)
    }
  }
}

/** The autoplugin which adapts the old sbt plugin classes into a legitimate AutoPlugin.
 *
 * This will add the ability to call git directly in the sbt shell via a command, as well as add
 * the infrastructure to read git properties.
 *
 * We keep the old SbtGit object around in an attempt not to break projects which depend on the old
 * plugin directly.
 */
object GitPlugin extends AutoPlugin {
  override def requires: Plugins = sbt.plugins.CorePlugin
  override def trigger = allRequirements
  // Note: In an attempt to pretend we are binary compatible, we current add this as an after thought.
  // In 1.0, we should deprecate/move the other means of getting these values.
  //noinspection ScalaUnusedSymbol
  object autoImport {
    val git: SbtGit.git.type = SbtGit.git
    def versionWithGit: Seq[sbt.Setting[_]] = SbtGit.versionWithGit
    def versionProjectWithGit: Seq[sbt.Setting[_]] = SbtGit.versionProjectWithGit
    def useJGit: sbt.Setting[_] = SbtGit.useJGit
    def showCurrentGitBranch: sbt.Setting[_] = SbtGit.showCurrentGitBranch
  }
  override def buildSettings: Seq[Setting[_]] = SbtGit.buildSettings
  override def projectSettings: Seq[Setting[_]] = SbtGit.projectSettings
}

/** Adapter to auto-enable git versioning.  i.e. the sbt 0.13.5+ mechanism of turning it on. */
object GitVersioning extends AutoPlugin {
  override def requires: Plugins = sbt.plugins.IvyPlugin && GitPlugin
  override def buildSettings: Seq[Def.Setting[_]] = GitPlugin.autoImport.versionWithGit
  override def projectSettings: Seq[Def.Setting[_]] = GitPlugin.autoImport.versionProjectWithGit
}
/** Adapter to enable the git prompt. i.e. rich prompt based on git info. */
object GitBranchPrompt extends AutoPlugin {
  override def requires: Plugins = GitPlugin
  override def projectSettings: Seq[Def.Setting[_]] = SbtGit.showCurrentGitBranch
}
