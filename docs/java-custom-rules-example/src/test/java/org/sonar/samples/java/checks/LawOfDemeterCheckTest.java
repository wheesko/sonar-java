package org.sonar.samples.java.checks;

import org.junit.jupiter.api.Test;
import org.sonar.java.checks.verifier.CheckVerifier;

class LawOfDemeterCheckTest {

  @Test
  void test() {
    CheckVerifier.newVerifier()
      .onFile("src/test/files/LawOfDemeterCheck.java")
      .withCheck(new LawOfDemeterCheck())
      .verifyIssues();

    CheckVerifier.newVerifier()
      .onFile("src/test/files/LawOfDemeterDemo.java")
      .withCheck(new LawOfDemeterCheck())
      .verifyIssues();

    CheckVerifier.newVerifier()
      .onFile("src/test/files/LawOfDemeterStreams.java")
      .withCheck(new LawOfDemeterCheck())
      .verifyNoIssues();

    CheckVerifier.newVerifier()
      .onFile("src/test/files/LawOfDemeterOptional.java")
      .withCheck(new LawOfDemeterCheck())
      .verifyNoIssues();
  }
}
