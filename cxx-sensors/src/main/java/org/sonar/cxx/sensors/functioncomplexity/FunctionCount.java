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

public class FunctionCount {

  public int countOverThreshold;
  public int countBelowThreshold;

  public FunctionCount(int countOverThreshold, int countBelowThreshold) {
    super();
    this.countOverThreshold = countOverThreshold;
    this.countBelowThreshold = countBelowThreshold;
  }

  public void add(FunctionCount other) {
    countOverThreshold += other.countOverThreshold;
    countBelowThreshold += other.countBelowThreshold;
  }

  public void reset() {
    countOverThreshold = 0;
    countBelowThreshold = 0;
  }

  public double getOverThresholdDensity() {
    double total = (double) countOverThreshold + (double) countBelowThreshold;
    if (total > 0) {
      return (countOverThreshold / total * 100.0);
    } else {
      return 0;
    }
  }

}
