/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2018 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
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
package org.sonar.cxx.checks.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.sonar.cxx.CxxAstScanner;
import org.sonar.cxx.checks.CxxFileTester;
import org.sonar.cxx.checks.CxxFileTesterHelper;
import org.sonar.cxx.utils.CxxReportIssue;
import org.sonar.cxx.utils.CxxReportLocation;
import org.sonar.cxx.visitors.MultiLocatitionSquidCheck;
import org.sonar.squidbridge.api.SourceFile;

public class FunctionCognitiveComplexityCheckTest {

  @Test
  public void check() throws UnsupportedEncodingException, IOException {
    FunctionCognitiveComplexityCheck check = new FunctionCognitiveComplexityCheck();
    check.setMax(18);
    CxxFileTester tester = CxxFileTesterHelper
        .CreateCxxFileTester("src/test/resources/checks/FunctionCognitiveComplexity.cc", ".");
    SourceFile file = CxxAstScanner.scanSingleFile(tester.cxxFile, tester.sensorContext,
        CxxFileTesterHelper.mockCxxLanguage(), check);

    Set<CxxReportIssue> issues = MultiLocatitionSquidCheck.getMultiLocationCheckMessages(file);
    assertThat(issues).isNotNull();
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(issues).hasSize(4);
    softly.assertThat(issues).allSatisfy(issue -> "FunctionCognitiveComplexity".equals(issue.getRuleId()));

    CxxReportIssue issue0 = issues.stream().filter(issue -> issue.getLocations().get(0).getLine().equals("13"))
        .findFirst().orElseThrow(() -> new AssertionError("No issue at line 13"));
    softly.assertThat(issue0.getLocations()).containsOnly(
        new CxxReportLocation(null, "13",
        "The Cognitive Complexity of this function is 20 which is greater than 18 authorized."));

    CxxReportIssue issue1 = issues.stream().filter(issue -> issue.getLocations().get(0).getLine().equals("33"))
        .findFirst().orElseThrow(() -> new AssertionError("No issue at line 33"));
    softly.assertThat(issue1.getLocations()).containsOnly(
        new CxxReportLocation(null, "33",
        "The Cognitive Complexity of this function is 20 which is greater than 18 authorized."));

    CxxReportIssue issue2 = issues.stream().filter(issue -> issue.getLocations().get(0).getLine().equals("51"))
        .findFirst().orElseThrow(() -> new AssertionError("No issue at line 51"));
    softly.assertThat(issue2.getLocations()).containsOnly(
        new CxxReportLocation(null, "51",
        "The Cognitive Complexity of this function is 20 which is greater than 18 authorized."));

    CxxReportIssue issue3 = issues.stream().filter(issue -> issue.getLocations().get(0).getLine().equals("72"))
        .findFirst().orElseThrow(() -> new AssertionError("No issue at line 72"));
    softly.assertThat(issue3.getLocations()).containsOnly(
        new CxxReportLocation(null, "72",
        "The Cognitive Complexity of this function is 20 which is greater than 18 authorized."));

    softly.assertAll();
  }

}
