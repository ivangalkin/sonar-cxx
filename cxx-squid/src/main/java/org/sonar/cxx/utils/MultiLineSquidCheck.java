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
package org.sonar.cxx.utils;

import java.util.HashSet;
import java.util.Set;

import org.sonar.api.utils.AnnotationUtils;
import org.sonar.squidbridge.SquidAstVisitorContext;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.checks.SquidCheck;
import org.sonar.squidbridge.measures.CalculatedMetricFormula;
import org.sonar.squidbridge.measures.MetricDef;

import com.sonar.sslr.api.Grammar;

public class MultiLineSquidCheck<G extends Grammar> extends SquidCheck<G> {

  static enum DataKey implements MetricDef {
    MULTI_LINE_ISSUES;

    @Override
    public String getName() {
      return MULTI_LINE_ISSUES.getName();
    }

    @Override
    public boolean isCalculatedMetric() {
      return false;
    }

    @Override
    public boolean aggregateIfThereIsAlreadyAValue() {
      return false;
    }

    @Override
    public boolean isThereAggregationFormula() {
      return false;
    }

    @Override
    public CalculatedMetricFormula getCalculatedMetricFormula() {
      return null;
    }
  }

  protected String getRuleKey() {
    org.sonar.check.Rule ruleAnnotation = AnnotationUtils.getAnnotation(this, org.sonar.check.Rule.class);
    if (ruleAnnotation != null && ruleAnnotation.key() != null) {
      return ruleAnnotation.key();
    }
    throw new IllegalStateException("Check must be annotated with @Rule( key = <key> )");
  }

  private SourceFile getSourceFile() {
    final SquidAstVisitorContext<G> c = getContext();
    if (c.peekSourceCode() instanceof SourceFile) {
      return (SourceFile) c.peekSourceCode();
    } else if (c.peekSourceCode().getParent(SourceFile.class) != null) {
      return c.peekSourceCode().getParent(SourceFile.class);
    } else {
      throw new IllegalStateException("Unable to get SourceFile on source code '"
          + (c.peekSourceCode() == null ? "[NULL]" : c.peekSourceCode().getKey()) + "'");
    }
  }

  protected void saveMultilineCheckMessage(CxxReportIssue message) {
    SourceFile sourceFile = getSourceFile();
    Set<CxxReportIssue> messages = getMultilineCheckMessages(sourceFile);
    if (messages == null) {
      messages = new HashSet<>();
    }
    messages.add(message);
    setMultilineCheckMessages(sourceFile, messages);
  }

  @SuppressWarnings("unchecked")
  public static Set<CxxReportIssue> getMultilineCheckMessages(SourceFile sourceFile) {
    return (Set<CxxReportIssue>) sourceFile.getData(DataKey.MULTI_LINE_ISSUES);
  }

  public static boolean hasMultilineCheckMessages(SourceFile sourceFile) {
    Set<CxxReportIssue> issues = getMultilineCheckMessages(sourceFile);
    return issues != null && !issues.isEmpty();
  }

  private static void setMultilineCheckMessages(SourceFile sourceFile, Set<CxxReportIssue> messages) {
    sourceFile.addData(DataKey.MULTI_LINE_ISSUES, messages);
  }

  public static void eraseMultilineCheckMessages(SourceFile sourceFile) {
    setMultilineCheckMessages(sourceFile, null);
  }
}
