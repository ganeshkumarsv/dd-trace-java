package datadog.trace.civisibility.ci;

import static datadog.trace.civisibility.utils.CIUtils.findParentPathBackwards;

import datadog.trace.api.civisibility.ci.CIInfo;
import datadog.trace.api.civisibility.ci.CIProviderInfo;
import datadog.trace.api.git.GitInfo;
import java.nio.file.Path;

/**
 * This class is the strategy to use when the CI provider used to execute the tests cannot be
 * recognized. See selectCI() method in {@code CIProviderInfo} class.
 *
 * <p>In this case, the tests may be being executed either a local development or an unknown CI
 * provider, so we cannot collect the usual data like pipeline, stage, job, etc.
 *
 * <p>However, we would like to provide git information if the user is using git, so we infer the
 * workspace path leveraging the `.git` folder, which is usually kept in the root path of the
 * repository.
 *
 * <p>The workspace path will be used in the CIProviderInfo constructor to access the `.git` folder
 * and calculate the git information properly.
 */
class UnknownCIInfo implements CIProviderInfo {

  public static final String UNKNOWN_PROVIDER_NAME = "unknown";

  private final String targetFolder;
  private final Path currentPath;

  UnknownCIInfo(Path currentPath) {
    this(".git", currentPath);
  }

  UnknownCIInfo(String targetFolder, Path currentPath) {
    this.targetFolder = targetFolder;
    this.currentPath = currentPath;
  }

  @Override
  public GitInfo buildCIGitInfo() {
    return GitInfo.NOOP;
  }

  @Override
  public CIInfo buildCIInfo() {
    final Path workspace = findParentPathBackwards(getCurrentPath(), getTargetFolder(), true);
    if (workspace == null) {
      return CIInfo.NOOP;
    }

    return CIInfo.builder().ciWorkspace(workspace.toAbsolutePath().toString()).build();
  }

  protected String getTargetFolder() {
    return targetFolder;
  }

  protected Path getCurrentPath() {
    return currentPath;
  }
}
