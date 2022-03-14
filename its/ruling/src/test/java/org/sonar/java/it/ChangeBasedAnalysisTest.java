/*
 * SonarQube Java
 * Copyright (C) 2013-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.it;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ChangeBasedAnalysisTest {
  // Feature configuration
  private static String INCREMENTAL_ANALYSIS_KEY = "sonar.java.internal.skipUnchanged";
  private static boolean INCREMENTAL_ANALYSIS_VALUE = true;

  // Project configuration
  private static String PROJECT_KEY = "change-based-analysis";
  private static String PROJECT_NAME = PROJECT_KEY;
  private static Path PROJECT_LOCATION = Paths.get("..", "..", "its", "ruling", "src", "test", "resources").toAbsolutePath().normalize();
  private static Path TARGET_ACTUAL = Paths.get("target", "actual").toAbsolutePath();

  @ClassRule
  public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @ClassRule
  public static Orchestrator orchestrator = Orchestrator.builderEnv()
    .setSonarVersion(System.getProperty("sonar.runtimeVersion", "LATEST_RELEASE[9.3]"))
    .setEdition(Edition.DEVELOPER)
    .addPlugin(FileLocation.byWildcardMavenFilename(new File("../../sonar-java-plugin/target"), "sonar-java-plugin-*.jar"))
    .addPlugin(MavenLocation.of("org.sonarsource.sonar-lits-plugin", "sonar-lits-plugin", "0.10.0.2181"))
    .build();

  @BeforeClass
  public static void prepare_quality_profiles() throws Exception {
    ImmutableSet<String> subsetOfEnabledRules = ImmutableSet.copyOf(
        Splitter.on(',').trimResults().omitEmptyStrings().splitToList(
        System.getProperty("rules", "")
      )
    );
    OrchestrationUtils.prepare_quality_profiles(
      orchestrator,
      subsetOfEnabledRules,
      temporaryFolder
    );
  }

  private static void provisionProject(Orchestrator ORCHESTRATOR, String projectKey, String projectName, String languageKey, String profileName) {
    Server server = ORCHESTRATOR.getServer();
    server.provisionProject(projectKey, projectName);
    server.associateProjectToQualityProfile(projectKey, languageKey, profileName);
  }

  private static MavenBuild setupBuild(String branch) throws IOException {
    String folderName = getBranchFolderBasename(branch);
    Path correctConfigIssues = TARGET_ACTUAL.resolve(folderName + "-issues").toAbsolutePath();
    Path projectLocation = PROJECT_LOCATION.resolve(folderName);
    String binaries = projectLocation.resolve(Paths.get("target", "classes")).toString();
    return MavenBuild.create()
      // Set up incremental analysis
      .setProperty(INCREMENTAL_ANALYSIS_KEY, Boolean.toString(INCREMENTAL_ANALYSIS_VALUE))
      // Set up project configuration
      .setPom(projectLocation.resolve("pom.xml").toFile().getCanonicalFile())
      .setCleanPackageSonarGoals()
      .setProperty("sonar.java.binaries", binaries)
      .addArgument("-Prules")
      .setProperty("sonar.projectKey", PROJECT_KEY)
      .setProperty("sonar.projectName", PROJECT_NAME)
      // common properties
      .setProperty("sonar.cpd.exclusions", "**/*")
      .setProperty("sonar.skipPackageDesign", "true")
      .setProperty("sonar.internal.analysis.failFast", "true")
      // issues output configuration
      .setProperty("sonar.lits.dump.old", correctConfigIssues.toString())
      .setProperty("sonar.lits.dump.new", createTempFolder(branch).toAbsolutePath().toString())
      .setProperty("sonar.lits.differences", TARGET_ACTUAL.resolve(folderName + ".differences").toString());
  }

  @Test
  public void test() throws IOException {
    provisionProject(orchestrator, PROJECT_KEY, PROJECT_NAME, "java", "rules");

    String mainBranch = "main";
    orchestrator.executeBuild(setupBuild(mainBranch));
    assertNoDifferences(mainBranch);

    for (String branch: List.of("branch-same", "branch-diff")) {
      MavenBuild mavenBuild = setupBuild(branch)
        .setProperties(
          "sonar.pullrequest.key", branch,
          "sonar.pullrequest.branch", branch,
          "sonar.pullrequest.base", mainBranch,
          "sonar.scm.provider", "git",
          "sonar.scm.disabled", "false"
        );
      orchestrator.executeBuild(mavenBuild);
      assertNoDifferences(branch);
      //TODO assert that optimization is documented in the logs
      //TODO assert that issues are only raised on files that have changed
    }
  }

  private static void assertNoDifferences(String branchName) throws IOException {
    String differences = Files.readString(getDifferencesPath(branchName));
    Assertions.assertThat(differences).isEmpty();
  }

  private static Path getDifferencesPath(String branch) {
    return TARGET_ACTUAL.resolve(getBranchFolderBasename(branch) + ".differences");
  }

  private static String getBranchFolderBasename(String branch) {
    return PROJECT_NAME + "-" + branch;
  }

  private static Path createTempFolder(String branch) throws IOException {
    return temporaryFolder.newFolder(getBranchFolderBasename(branch)).toPath();
  }
}