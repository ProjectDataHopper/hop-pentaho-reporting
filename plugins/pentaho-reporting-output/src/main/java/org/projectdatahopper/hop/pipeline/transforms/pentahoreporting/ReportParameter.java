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

import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Maps a report parameter name to an input field. */
@Getter
@Setter
@NoArgsConstructor
public class ReportParameter implements Serializable {
  private static final long serialVersionUID = 1L;

  @HopMetadataProperty(key = "name")
  private String parameterName;

  @HopMetadataProperty(key = "field")
  private String fieldName;

  public ReportParameter(String parameterName, String fieldName) {
    this.parameterName = parameterName;
    this.fieldName = fieldName;
  }
}
