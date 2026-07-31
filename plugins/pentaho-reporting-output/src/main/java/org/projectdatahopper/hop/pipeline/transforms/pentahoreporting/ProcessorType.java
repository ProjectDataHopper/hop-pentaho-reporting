/*
 * Copyright (C) 2026 Project Data Hopper
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package org.projectdatahopper.hop.pipeline.transforms.pentahoreporting;

import java.util.Arrays;

/**
 * Output formats supported by the classic reporting engine. Codes match the PDI
 * {@code PentahoReportingOutput} step for migration compatibility.
 */
public enum ProcessorType {
  PDF("PDF", "PDF"),
  PagedHTML("PagedHtml", "Paged HTML"),
  StreamingHTML("StreamingHtml", "Streaming HTML"),
  CSV("CSV", "CSV"),
  Excel("Excel", "Excel"),
  Excel_2007("Excel 2007", "Excel 2007"),
  RTF("RTF", "RTF");

  private final String code;
  private final String description;

  ProcessorType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  public static String[] getDescriptions() {
    return Arrays.stream(values()).map(ProcessorType::getDescription).toArray(String[]::new);
  }

  public static ProcessorType getByCode(String code) {
    if (code == null) {
      return null;
    }
    for (ProcessorType type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    return null;
  }

  public static ProcessorType getByDescription(String description) {
    if (description == null) {
      return null;
    }
    for (ProcessorType type : values()) {
      if (type.description.equals(description)) {
        return type;
      }
    }
    return null;
  }
}
