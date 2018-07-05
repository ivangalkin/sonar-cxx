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
package org.sonar.cxx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sonar.api.measures.Metric;

/**
 * Some metrics are language specific. The exact reason for that might be the
 * coexistence of CXX and C plugins in the same project/module. This concept
 * doesn't match with CoreMetrics e.g. complexity, issues number etc are
 * language independent; for multi-language modules CoreMetrics are accumulated.
 *
 * Unification of metrics should be discussed in the future.
 *
 */
public class CxxMetricsFactory {

  private CxxMetricsFactory() {
  }

  public enum Key {
    // Introduce own documentation metrics, after they has been removed from SQ core
    // see https://jira.sonarsource.com/browse/SONAR-8328
    PUBLIC_API_KEY("public_api"),
    PUBLIC_UNDOCUMENTED_API_KEY("public_undocumented_api"),
    PUBLIC_DOCUMENTED_API_DENSITY_KEY("public_documented_api_density"),

    // Introduce metric keys for sensors - number of issues per file / per module
    CLANG_SA_SENSOR_ISSUES_KEY("ClangSA"),
    CLANG_TIDY_SENSOR_ISSUES_KEY("Clang-Tidy"),
    COMPILER_SENSOR_ISSUES_KEY("Compiler"),
    CPPCHECK_SENSOR_ISSUES_KEY("CppCheck"),
    DRMEMORY_SENSOR_ISSUES_KEY("DrMemory"),
    OTHER_SENSOR_ISSUES_KEY("other"),
    PCLINT_SENSOR_ISSUES_KEY("PC-Lint"),
    RATS_SENSOR_ISSUES_KEY("Rats"),
    SQUID_SENSOR_ISSUES_KEY("Squid"),
    VALGRIND_SENSOR_KEY("Valgrind"),
    VERAXX_SENSOR_KEY("Vera++");

    String getLanguageSpecificKey(String langPropertiesKey)
    {
      return langPropertiesKey.toUpperCase(Locale.ENGLISH) + "-" + key;
    }

    Key(final String key) {
      this.key = key.toUpperCase(Locale.ENGLISH);
    }

    @Override
    public String toString() {
      return key;
    }

    private final String key;
  }

  /**
   * Generate a map
   * <code>{ language independent metric key : language dependent Metric object }</code>
   */
  public static Map<CxxMetricsFactory.Key, Metric<?>> generateMap(String langKey, String langPropertiesKey) {
    final String domain = langKey.toUpperCase(Locale.ENGLISH);

    final Map<CxxMetricsFactory.Key, Metric<?>> metrics = new HashMap<>();

    final Metric<Integer> PUBLIC_API = new Metric.Builder(Key.PUBLIC_API_KEY.getLanguageSpecificKey(langPropertiesKey),
        "Public API", Metric.ValueType.INT)
        .setDescription("Public API")
        .setDirection(Metric.DIRECTION_WORST)
         .setQualitative(false)
         .setDomain(domain)
         .create();
    metrics.put(Key.PUBLIC_API_KEY, PUBLIC_API);

    final Metric<Double> PUBLIC_DOCUMENTED_API_DENSITY = new Metric.Builder(
        Key.PUBLIC_DOCUMENTED_API_DENSITY_KEY.getLanguageSpecificKey(langPropertiesKey), "Public Documented API (%)",
        Metric.ValueType.PERCENT)
        .setDescription("Public documented classes and functions balanced by ncloc")
        .setDirection(Metric.DIRECTION_BETTER)
        .setQualitative(true)
        .setDomain(domain)
        .setWorstValue(0.0)
        .setBestValue(100.0)
        .setOptimizedBestValue(true)
        .create();
    metrics.put(Key.PUBLIC_DOCUMENTED_API_DENSITY_KEY, PUBLIC_DOCUMENTED_API_DENSITY);

    final Metric<Integer> PUBLIC_UNDOCUMENTED_API = new Metric.Builder(
        Key.PUBLIC_UNDOCUMENTED_API_KEY.getLanguageSpecificKey(langPropertiesKey), "Public Undocumented API",
        Metric.ValueType.INT)
        .setDescription("Public undocumented classes, functions and variables")
        .setDirection(Metric.DIRECTION_WORST)
        .setQualitative(true)
        .setDomain(domain)
        .setBestValue(0.0)
        .setOptimizedBestValue(true)
        .create();
    metrics.put(Key.PUBLIC_UNDOCUMENTED_API_KEY, PUBLIC_UNDOCUMENTED_API);

    addSensorMetric(Key.CLANG_SA_SENSOR_ISSUES_KEY, "ClangSA issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.CLANG_TIDY_SENSOR_ISSUES_KEY, "ClangTidy issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.COMPILER_SENSOR_ISSUES_KEY, "Compiler issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.CPPCHECK_SENSOR_ISSUES_KEY, "CppCheck issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.DRMEMORY_SENSOR_ISSUES_KEY, "DrMemory issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.OTHER_SENSOR_ISSUES_KEY, "Other tools issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.PCLINT_SENSOR_ISSUES_KEY, "PC-Lint issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.RATS_SENSOR_ISSUES_KEY, "Rats issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.SQUID_SENSOR_ISSUES_KEY, "Squid issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.VALGRIND_SENSOR_KEY, "Valgrind issues", domain, langPropertiesKey, metrics);
    addSensorMetric(Key.VERAXX_SENSOR_KEY, "Vera issues", domain, langPropertiesKey, metrics);

    return metrics;
  }

  public static List<Metric> generateList(String langKey, String langPropertiesKey) {
    return new ArrayList<>(generateMap(langKey, langPropertiesKey).values());
  }

  private static void addSensorMetric(Key metricKey, String description, String domain, String langPropertiesKey,
      Map<Key, Metric<?>> metrics) {
    Metric<Integer> metric = new Metric.Builder(metricKey.getLanguageSpecificKey(langPropertiesKey), description,
        Metric.ValueType.INT)
        .setDescription(description)
        .setDirection(Metric.DIRECTION_WORST)
        .setDomain(domain)
        .setBestValue(0.0)
        .setOptimizedBestValue(true)
        .create();
    metrics.put(metricKey, metric);
  }
}
