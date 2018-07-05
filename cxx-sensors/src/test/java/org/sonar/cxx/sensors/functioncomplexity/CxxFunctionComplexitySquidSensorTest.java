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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.*;
import static org.mockito.Mockito.*;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.cxx.CxxAstScanner;
import org.sonar.cxx.CxxLanguage;
import org.sonar.cxx.sensors.utils.TestUtils;
import org.sonar.squidbridge.api.SourceFile;

public class CxxFunctionComplexitySquidSensorTest {

  private FileLinesContextFactory fileLinesContextFactory;
  private FileLinesContext fileLinesContext;
  private CxxLanguage language;
  private SensorContextTester context;
  private CxxFunctionComplexitySquidSensor sensor;

  @Before
  public void setUp() {
    fileLinesContextFactory = mock(FileLinesContextFactory.class);
    fileLinesContext = mock(FileLinesContext.class);

    language = TestUtils.mockCxxLanguage();
    when(language.getIntegerOption(CxxFunctionComplexitySquidSensor.FUNCTION_COMPLEXITY_THRESHOLD_KEY))
        .thenReturn(Optional.of(5));

    sensor = new CxxFunctionComplexitySquidSensor(language);
  }

  private DefaultInputFile getInputFile() throws IOException {
    File baseDir = TestUtils.loadResource("/org/sonar/cxx/sensors");
    File target = new File(baseDir, "FunctionComplexity.cc");

    String content = new String(Files.readAllBytes(target.toPath()), "UTF-8");
    DefaultInputFile inputFile = TestInputFileBuilder.create("ProjectKey", baseDir, target).setContents(content)
        .setCharset(Charset.forName("UTF-8")).setLanguage(language.getKey()).setType(InputFile.Type.MAIN).build();

    context = SensorContextTester.create(baseDir);
    context.fileSystem().add(inputFile);

    when(fileLinesContextFactory.createFor(inputFile)).thenReturn(fileLinesContext);

    return inputFile;
  }

  private DefaultInputFile getEmptyInputFile() throws IOException {
    File baseDir = TestUtils.loadResource("/org/sonar/cxx/sensors");
    File target = new File(baseDir, "EmptyFile.cc");

    String content = new String(Files.readAllBytes(target.toPath()), "UTF-8");
    DefaultInputFile inputFile = TestInputFileBuilder.create("ProjectKey", baseDir, target).setContents(content)
        .setCharset(Charset.forName("UTF-8")).setLanguage(language.getKey()).setType(InputFile.Type.MAIN).build();

    context = SensorContextTester.create(baseDir);
    context.fileSystem().add(inputFile);

    when(fileLinesContextFactory.createFor(inputFile)).thenReturn(fileLinesContext);

    return inputFile;
  }

  @Test
  public void testPublishMeasuresForProject() throws IOException {
    DefaultInputFile inputFile = getInputFile();

    CxxAstScanner.scanSingleFile(inputFile, context, TestUtils.mockCxxLanguage(), sensor.getVisitor());
    sensor.publishMeasureForProject(context.module(), context);
    final String moduleKey = context.module().key();

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS).value()).isEqualTo(4);
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC).value()).isEqualTo(44);
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_PERC).value()).isEqualTo(40.0);
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC_PERC).value()).isEqualTo(80);
    softly.assertAll();
  }

  @Test
  public void testPublishMeasuresForEmptyProject() throws IOException {
    DefaultInputFile inputFile = getEmptyInputFile();

    CxxAstScanner.scanSingleFile(inputFile, context, TestUtils.mockCxxLanguage(), sensor.getVisitor());
    sensor.publishMeasureForProject(context.module(), context);
    final String moduleKey = context.module().key();

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS).value()).isEqualTo(0);
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC).value()).isEqualTo(0);
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_PERC).value()).isEqualTo(0);
    softly.assertThat(context.measure(moduleKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC_PERC).value()).isEqualTo(0);
    softly.assertAll();
  }

  @Test
  public void testPublishMeasuresForFile() throws IOException {
    DefaultInputFile inputFile = getInputFile();

    SourceFile squidFile = CxxAstScanner.scanSingleFile(inputFile, context, TestUtils.mockCxxLanguage(), sensor.getVisitor());
    sensor.publishMeasureForFile(inputFile, squidFile, context);
    final String fileKey = inputFile.key();

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS).value()).isEqualTo(4);
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC).value()).isEqualTo(44);
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_PERC).value()).isEqualTo(40.0);
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC_PERC).value()).isEqualTo(80);
    softly.assertAll();
  }

  @Test
  public void testPublishMeasuresForEmptyFile() throws IOException {
    DefaultInputFile inputFile = getEmptyInputFile();

    SourceFile squidFile = CxxAstScanner.scanSingleFile(inputFile, context, TestUtils.mockCxxLanguage(), sensor.getVisitor());
    sensor.publishMeasureForFile(inputFile, squidFile, context);
    final String fileKey = inputFile.key();

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS).value()).isEqualTo(0);
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC).value()).isEqualTo(0);
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_PERC).value()).isEqualTo(0);
    softly.assertThat(context.measure(fileKey, FunctionComplexityMetrics.COMPLEX_FUNCTIONS_LOC_PERC).value()).isEqualTo(0);
    softly.assertAll();
  }
}
