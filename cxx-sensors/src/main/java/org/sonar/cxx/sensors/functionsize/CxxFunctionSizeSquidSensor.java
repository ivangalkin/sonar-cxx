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
package org.sonar.cxx.sensors.functionsize;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import org.sonar.cxx.sensors.functioncomplexity.FunctionCount;
import org.sonar.cxx.visitors.CxxMetricsAggragator;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.api.SourceFunction;

//TODO move from cxx-sensors to cxx-squid

/**
 *
 * {@link SquidAstVisitor} that collects function sizes for each file and stores
 * this measurements in the internal maps. These maps are used for later metrics
 * calculation for entire module.
 *
 * ATTENTION! From performance reasons the maps are not synchronized. For
 * parallel file processing this must be changed.
 *
 */
public class CxxFunctionSizeSquidSensor extends SquidAstVisitor<Grammar> implements CxxMetricsAggragator {

  private static final Logger LOG = Loggers.get(CxxFunctionSizeSquidSensor.class);

  public static final String FUNCTION_SIZE_THRESHOLD_KEY = "funcsize.threshold";

  private int sizeThreshold = 0;

  private FunctionCount currentFile_bigFunctions;
  private FunctionCount currentFile_locInBigFunction;

  private FunctionCount currentModule_bigFunctions;
  private FunctionCount currentModule_locInBigFunctions;

  private Map<SourceFile, FunctionCount> bigFunctionsPerFile;

  private Map<SourceFile, FunctionCount> locInBigFunctionsPerFile;

  public CxxFunctionSizeSquidSensor(CxxLanguage language) {
    this.sizeThreshold = language.getIntegerOption(FUNCTION_SIZE_THRESHOLD_KEY).orElse(20);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Function size threshold: " + this.sizeThreshold);
    }
  }

  @Override
  public void init() {
    subscribeTo(CxxGrammarImpl.functionBody);

    currentFile_bigFunctions = new FunctionCount(0, 0);
    currentFile_locInBigFunction = new FunctionCount(0, 0);

    currentModule_bigFunctions = new FunctionCount(0, 0);
    currentModule_locInBigFunctions = new FunctionCount(0, 0);

    bigFunctionsPerFile = new HashMap<>();
    locInBigFunctionsPerFile = new HashMap<>();
  }

  @Override
  public void leaveNode(AstNode node) {
    SourceFunction sourceFunction = (SourceFunction) getContext().peekSourceCode();

    final int lineCount = sourceFunction.getInt(CxxMetric.LINES_OF_CODE_IN_FUNCTION_BODY);

    if (lineCount > this.sizeThreshold) {
      currentFile_bigFunctions.countOverThreshold++;
      currentFile_locInBigFunction.countOverThreshold += lineCount;
    } else {
      currentFile_bigFunctions.countBelowThreshold++;
      currentFile_locInBigFunction.countBelowThreshold += lineCount;
    }
  }

  @Override
  public void visitFile(AstNode astNode) {
    currentFile_bigFunctions.reset();
    currentFile_locInBigFunction.reset();

    super.visitFile(astNode);
  }

  @Override
  public void leaveFile(AstNode astNode) {
    super.leaveFile(astNode);

    SourceFile sourceFile = (SourceFile) getContext().peekSourceCode();

    currentModule_bigFunctions.add(currentFile_bigFunctions);
    currentModule_locInBigFunctions.add(currentFile_locInBigFunction);

    bigFunctionsPerFile.put(sourceFile,
        new FunctionCount(currentFile_bigFunctions.countOverThreshold, currentFile_bigFunctions.countBelowThreshold));
    locInBigFunctionsPerFile.put(sourceFile, new FunctionCount(currentFile_locInBigFunction.countOverThreshold,
        currentFile_locInBigFunction.countBelowThreshold));
  }

  @Override
  public SquidAstVisitor<Grammar> getVisitor() {
    return this;
  }

  private void publishMeasure(SensorContext context, FunctionCount count, InputComponent component,
      Optional<Metric<Integer>> totalMetric, Metric<Integer> overThresholdMetric,
      Metric<Double> overThresholdDensityMetric) {

    if (totalMetric.isPresent()) {
      context.<Integer>newMeasure().forMetric(totalMetric.get()).on(component)
          .withValue(count.countOverThreshold + count.countBelowThreshold).save();
    }

    context.<Integer>newMeasure().forMetric(overThresholdMetric).on(component).withValue(count.countOverThreshold)
        .save();

    context.<Double>newMeasure().forMetric(overThresholdDensityMetric).on(component)
        .withValue(count.getOverThresholdDensity()).save();
  }

  @Override
  public void publishMeasureForFile(InputFile inputFile, SourceFile squidFile, SensorContext context) {
    FunctionCount bigFunctions = bigFunctionsPerFile.get(squidFile);
    if (bigFunctions != null) {
      publishMeasure(context, bigFunctions, inputFile, Optional.empty(), FunctionSizeMetrics.BIG_FUNCTIONS,
          FunctionSizeMetrics.BIG_FUNCTIONS_PERC);
    }

    FunctionCount locInBigFunctions = locInBigFunctionsPerFile.get(squidFile);
    if (locInBigFunctions != null) {
      publishMeasure(context, locInBigFunctions, inputFile, Optional.of(FunctionSizeMetrics.LOC_IN_FUNCTIONS),
          FunctionSizeMetrics.BIG_FUNCTIONS_LOC, FunctionSizeMetrics.BIG_FUNCTIONS_LOC_PERC);
    }
  }

  @Override
  public void publishMeasureForProject(InputModule module, SensorContext context) {
    if (!bigFunctionsPerFile.isEmpty() && !locInBigFunctionsPerFile.isEmpty()) {
      publishMeasure(context, currentModule_bigFunctions, module, Optional.empty(), FunctionSizeMetrics.BIG_FUNCTIONS,
          FunctionSizeMetrics.BIG_FUNCTIONS_PERC);
      publishMeasure(context, currentModule_locInBigFunctions, module,
          Optional.of(FunctionSizeMetrics.LOC_IN_FUNCTIONS), FunctionSizeMetrics.BIG_FUNCTIONS_LOC,
          FunctionSizeMetrics.BIG_FUNCTIONS_LOC_PERC);
    }
  }
}
