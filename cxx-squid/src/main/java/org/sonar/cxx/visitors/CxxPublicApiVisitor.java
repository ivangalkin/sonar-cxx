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
package org.sonar.cxx.visitors;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.api.Token;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.Metric;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.cxx.CxxLanguage;
import org.sonar.cxx.CxxMetricsFactory;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.squidbridge.api.SourceFile;

/**
 * Visitor that counts documented and undocumented API items.<br>
 *
 * Per-file measurements are store in the internal map. This map is used for later
 * metrics calculation for entire module.
 *
 * ATTENTION! From performance reasons the map is not synchronized. For
 * parallel file processing this must be changed.
 *
 * Following items are counted as public API:
 * <ul>
 * <li>classes/structures</li>
 * <li>class members (public and protected)</li>
 * <li>structure members</li>
 * <li>enumerations</li>
 * <li>enumeration values</li>
 * <li>typedefs</li>
 * <li>functions</li>
 * <li>variables</li>
 * </ul>
 * <p>
 * Public API items are considered documented if they have Doxygen comments.<br>
 * Function arguments are not counted since they can be documented in function documentation and this visitor does not
 * parse Doxygen comments.<br>
 * This visitor should be applied only on header files.<br>
 * Currently, no filtering is applied using preprocessing directive.<br>
 * <p>
 * Limitation: only "in front of the declaration" comments and inline comments (for members) are considered. Documenting
 * public API by name (\struct Foo for instance) in other files is not supported.
 *
 * @see <a href="http://www.stack.nl/~dimitri/doxygen/manual/docblocks.html">
 * Doxygen Manual: Documenting the code</a>
 *
 * @author Ludovic Cintrat
 *
 * @param <GRAMMAR>
 */
public class CxxPublicApiVisitor<GRAMMAR extends Grammar> extends
    AbstractCxxPublicApiVisitor<Grammar> implements CxxMetricsAggragator {

  private static final Logger LOG = Loggers.get(CxxPublicApiVisitor.class);

  private final Metric<Integer> totalAPIMetric;
  private final Metric<Integer> undocumentedAPIMetric;
  private final Metric<Double> documentedAPIDensityMetric;

  private class APICount {
    public APICount(int totalNr, int undocumentedNr) {
      super();
      this.totalNr = totalNr;
      this.undocumentedNr = undocumentedNr;
    }

    public int totalNr = 0;
    public int undocumentedNr = 0;

    public double getDensity() {
      if (totalNr > 0 && totalNr >= undocumentedNr) {
        return ((double) totalNr - (double) undocumentedNr) / totalNr * 100.0;
      }
      return 0;
    }
  }

  private APICount currentFileCounter;
  private APICount currentModuleCounter;
  private Map<SourceFile, APICount> apiCounterPerFile;


  public CxxPublicApiVisitor(CxxLanguage language) {
    super();
    totalAPIMetric = language.<Integer>getMetric(CxxMetricsFactory.Key.PUBLIC_API_KEY);
    undocumentedAPIMetric = language.<Integer>getMetric(CxxMetricsFactory.Key.PUBLIC_UNDOCUMENTED_API_KEY);
    documentedAPIDensityMetric = language.<Double>getMetric(CxxMetricsFactory.Key.PUBLIC_DOCUMENTED_API_DENSITY_KEY);
    withHeaderFileSuffixes(Arrays.asList(language.getHeaderFileSuffixes()));
  }

  @Override
  public void init() {
    super.init();
    currentFileCounter = new APICount(0, 0);
    currentModuleCounter = new APICount(0, 0);
    apiCounterPerFile = new HashMap<>();
  }

  @Override
  public void visitFile(AstNode astNode) {
    currentFileCounter.totalNr = 0;
    currentFileCounter.undocumentedNr = 0;
    super.visitFile(astNode);
  }

  @Override
  public void leaveFile(AstNode astNode) {
    super.leaveFile(astNode);

    SourceFile sourceFile = (SourceFile) getContext().peekSourceCode();
    currentModuleCounter.totalNr += currentFileCounter.totalNr;
    currentModuleCounter.undocumentedNr += currentFileCounter.undocumentedNr;

    apiCounterPerFile.put(sourceFile,
        new APICount(currentFileCounter.totalNr, currentFileCounter.undocumentedNr));
  }

  @Override
  protected void onPublicApi(AstNode node, String id, List<Token> comments) {
    boolean commented = !comments.isEmpty();

    LOG.debug("node: {} line: {} id: '{}' documented: {}",
      node.getType(), node.getTokenLine(), id, commented);

    if (!commented) {
      currentFileCounter.undocumentedNr++;
    }

    currentFileCounter.totalNr++;
  }

  @Override
  public SquidAstVisitor<Grammar> getVisitor() {
    return this;
  }

  @Override
  public void publishMeasureForFile(InputFile inputFile, SourceFile squidFile, SensorContext context) {
    APICount c = apiCounterPerFile.get(squidFile);
    if (c == null) {
      c = new APICount(0, 0);
    }

    context.<Integer>newMeasure()
      .forMetric(totalAPIMetric)
      .on(inputFile)
      .withValue(c.totalNr)
      .save();

    context.<Integer>newMeasure()
      .forMetric(undocumentedAPIMetric)
      .on(inputFile)
      .withValue(c.undocumentedNr)
      .save();

    context.<Double>newMeasure()
      .forMetric(documentedAPIDensityMetric)
      .on(inputFile)
      .withValue(c.getDensity())
      .save();

  }

  @Override
  public void publishMeasureForProject(InputModule module, SensorContext context) {
    context.<Integer>newMeasure()
      .forMetric(totalAPIMetric)
      .on(module)
      .withValue(currentModuleCounter.totalNr)
      .save();

    context.<Integer>newMeasure()
      .forMetric(undocumentedAPIMetric)
      .on(module)
      .withValue(currentModuleCounter.undocumentedNr)
      .save();

    context.<Double>newMeasure()
      .forMetric(documentedAPIDensityMetric)
      .on(module)
      .withValue(currentModuleCounter.getDensity())
      .save();
  }
}

