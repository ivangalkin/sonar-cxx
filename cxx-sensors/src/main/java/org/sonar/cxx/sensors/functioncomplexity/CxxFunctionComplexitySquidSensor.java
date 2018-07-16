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
package org.sonar.cxx.sensors.functioncomplexity;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;

import java.util.HashMap;
import java.util.Map;

import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.cxx.CxxLanguage;
import org.sonar.cxx.api.CxxMetric;
import org.sonar.cxx.parser.CxxGrammarImpl;
import org.sonar.cxx.visitors.CxxMetricsAggragator;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.api.SourceFunction;
import org.sonar.squidbridge.checks.ChecksHelper;

// TODO move from cxx-sensors to cxx-squid

/**
 *
 * {@link SquidAstVisitor} that collects function complexity for each file and
 * stores this measurements in the internal maps. These maps are used for later
 * metrics calculation for entire module.
 *
 * ATTENTION! From performance reasons the maps are not synchronized. For
 * parallel file processing this must be changed.
 *
 */
public class CxxFunctionComplexitySquidSensor extends SquidAstVisitor<Grammar> implements CxxMetricsAggragator {

  private static final Logger LOG = Loggers.get(CxxFunctionComplexitySquidSensor.class);

  public static final String FUNCTION_COMPLEXITY_THRESHOLD_KEY = "funccomplexity.threshold";

  private int cyclomaticComplexityThreshold;

  private FunctionCount currentFile_complexFunctions;
  private FunctionCount currentFile_locInComplexFunction;

  private FunctionCount currentModule_complexFunctions;
  private FunctionCount currentModule_locInComplexFunctions;

  private Map<SourceFile, FunctionCount> complexFunctionsPerFile;
  private Map<SourceFile, FunctionCount> locInComplexFunctionsPerFile;

  public CxxFunctionComplexitySquidSensor(CxxLanguage language) {
    this.cyclomaticComplexityThreshold = language.getIntegerOption(FUNCTION_COMPLEXITY_THRESHOLD_KEY).orElse(10);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cyclomatic complexity threshold: " + this.cyclomaticComplexityThreshold);
    }
  }

  @Override
  public SquidAstVisitor<Grammar> getVisitor() {
    return this;
  }

  @Override
  public void init() {
    subscribeTo(CxxGrammarImpl.functionBody);

    currentFile_complexFunctions = new FunctionCount(0, 0);
    currentFile_locInComplexFunction = new FunctionCount(0, 0);

    currentModule_complexFunctions = new FunctionCount(0, 0);
    currentModule_locInComplexFunctions = new FunctionCount(0, 0);

    complexFunctionsPerFile = new HashMap<>();
    locInComplexFunctionsPerFile = new HashMap<>();
  }

  @Override
  public void visitFile(AstNode astNode) {
    currentFile_complexFunctions.reset();
    currentFile_locInComplexFunction.reset();

    super.visitFile(astNode);
  }

  @Override
  public void leaveFile(AstNode astNode) {
    super.leaveFile(astNode);

    SourceFile sourceFile = (SourceFile) getContext().peekSourceCode();

    currentModule_complexFunctions.add(currentFile_complexFunctions);
    currentModule_locInComplexFunctions.add(currentFile_locInComplexFunction);

    complexFunctionsPerFile.put(sourceFile, new FunctionCount(currentFile_complexFunctions.countOverThreshold,
        currentFile_complexFunctions.countBelowThreshold));
    locInComplexFunctionsPerFile.put(sourceFile, new FunctionCount(currentFile_locInComplexFunction.countOverThreshold,
        currentFile_locInComplexFunction.countBelowThreshold));
  }

  @Override
  public void leaveNode(AstNode node) {
    SourceFunction sourceFunction = (SourceFunction) getContext().peekSourceCode();

    final int complexity = ChecksHelper.getRecursiveMeasureInt(sourceFunction, CxxMetric.COMPLEXITY);
    final int lineCount = sourceFunction.getInt(CxxMetric.LINES_OF_CODE_IN_FUNCTION_BODY);

    if (complexity > this.cyclomaticComplexityThreshold) {
      currentFile_complexFunctions.countOverThreshold++;
      currentFile_locInComplexFunction.countOverThreshold += lineCount;
    } else {
      currentFile_complexFunctions.countBelowThreshold++;
      currentFile_locInComplexFunction.countBelowThreshold += lineCount;
    }
  }

  private void publishMeasure(SensorContext context, FunctionCount count, InputComponent component,
      Metric<Integer> overThresholdMetric, Metric<Double> overThresholdDensityMetric) {
    context.<Integer>newMeasure().forMetric(overThresholdMetric).on(component).withValue(count.countOverThreshold)
    .save();

    context.<Double>newMeasure().forMetric(overThresholdDensityMetric).on(component)
    .withValue(count.getOverThresholdDensity()).save();
  }

  @Override
  public void publishMeasureForFile(InputFile inputFile, SourceFile squidFile, SensorContext context) {
    FunctionCount complexFunctions = complexFunctionsPerFile.get(squidFile);
    if (complexFunctions != null) {
      publishMeasure(context, complexFunctions, inputFile, FunctionComplexityMetrics.COMPLEX_FUNCTIONS,
          FunctionComplexityMetrics.COMPLEX_FUNCTIONS_PERC);
    }
    FunctionCount locInComplexFunctions = locInComplexFunctionsPerFile.get(squidFile);
    if (locInComplexFunctions != null) {
      publishMeasure(context, locInComplexFunctions, inputFile, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC,
          FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC_PERC);
    }
  }

  @Override
  public void publishMeasureForProject(InputModule module, SensorContext context) {
    if (!complexFunctionsPerFile.isEmpty() && !locInComplexFunctionsPerFile.isEmpty()) {
      publishMeasure(context, currentModule_complexFunctions, module, FunctionComplexityMetrics.COMPLEX_FUNCTIONS,
          FunctionComplexityMetrics.COMPLEX_FUNCTIONS_PERC);
      publishMeasure(context, currentModule_locInComplexFunctions, module,
          FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC_PERC);
    }
  }

}
