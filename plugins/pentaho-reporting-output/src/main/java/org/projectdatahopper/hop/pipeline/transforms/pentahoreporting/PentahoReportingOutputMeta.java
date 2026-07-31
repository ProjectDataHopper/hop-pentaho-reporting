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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Metadata for the Pentaho Reporting Output transform.
 *
 * <p>Transform id {@code PentahoReportingOutput} matches the PDI step so migrated pipelines resolve
 * the plugin. XML keys mirror the legacy step tags where practical.
 */
@Getter
@Setter
@Transform(
    id = "PentahoReportingOutput",
    image = "pentaho-reporting-output.svg",
    name = "i18n::PentahoReportingOutput.Name",
    description = "i18n::PentahoReportingOutput.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Output",
    keywords = "i18n::PentahoReportingOutput.Keyword",
    documentationUrl = "",
    classLoaderGroup = "pentaho-reporting")
public class PentahoReportingOutputMeta
    extends BaseTransformMeta<PentahoReportingOutput, PentahoReportingOutputData> {

  private static final Class<?> PKG = PentahoReportingOutputMeta.class;

  @HopMetadataProperty(
      key = "input_file_field",
      injectionKey = "INPUT_FILE_FIELD",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.InputFileField")
  private String inputFileField;

  @HopMetadataProperty(
      key = "output_file_field",
      injectionKey = "OUTPUT_FILE_FIELD",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.OutputFileField")
  private String outputFileField;

  @HopMetadataProperty(
      key = "input_file",
      injectionKey = "INPUT_FILE",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.InputFile")
  private String inputFile;

  @HopMetadataProperty(
      key = "output_file",
      injectionKey = "OUTPUT_FILE",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.OutputFile")
  private String outputFile;

  @HopMetadataProperty(
      key = "use_values_from_fields",
      injectionKey = "USE_VALUES_FROM_FIELDS",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.UseValuesFromFields")
  private boolean useValuesFromFields = true;

  @HopMetadataProperty(
      key = "create_parent_folder",
      injectionKey = "CREATE_PARENT_FOLDER",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.CreateParentFolder")
  private boolean createParentFolder;

  @HopMetadataProperty(
      key = "processor_type",
      injectionKey = "OUTPUT_PROCESSOR_TYPE",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.ProcessorType")
  private String processorTypeCode = ProcessorType.PDF.getCode();

  @HopMetadataProperty(
      key = "parameter",
      groupKey = "parameters",
      injectionKey = "PARAMETER",
      injectionGroupKey = "PARAMETERS",
      injectionKeyDescription = "PentahoReportingOutputMeta.Injection.Parameter")
  private List<ReportParameter> parameters = new ArrayList<>();

  public ProcessorType getProcessorType() {
    ProcessorType type = ProcessorType.getByCode(processorTypeCode);
    return type != null ? type : ProcessorType.PDF;
  }

  public void setProcessorType(ProcessorType processorType) {
    this.processorTypeCode =
        processorType != null ? processorType.getCode() : ProcessorType.PDF.getCode();
  }

  /** Convenience view used by the runtime (parameter name → field name). */
  public Map<String, String> getParameterFieldMap() {
    Map<String, String> map = new HashMap<>();
    if (parameters != null) {
      for (ReportParameter parameter : parameters) {
        if (parameter != null
            && !Utils.isEmpty(parameter.getParameterName())
            && !Utils.isEmpty(parameter.getFieldName())) {
          map.put(parameter.getParameterName(), parameter.getFieldName());
        }
      }
    }
    return map;
  }

  public void setParameterFieldMap(Map<String, String> parameterFieldMap) {
    List<ReportParameter> list = new ArrayList<>();
    if (parameterFieldMap != null) {
      for (Map.Entry<String, String> entry : parameterFieldMap.entrySet()) {
        list.add(new ReportParameter(entry.getKey(), entry.getValue()));
      }
    }
    this.parameters = list;
  }

  @Override
  public void setDefault() {
    useValuesFromFields = true;
    createParentFolder = false;
    processorTypeCode = ProcessorType.PDF.getCode();
    parameters = new ArrayList<>();
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {

    if (input.length == 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "PentahoReportingOutputMeta.CheckResult.NoInput"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "PentahoReportingOutputMeta.CheckResult.TransformReceivingInfo"),
              transformMeta));
    }

    if (useValuesFromFields) {
      if (Utils.isEmpty(inputFileField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "PentahoReportingOutputMeta.CheckResult.InputFileFieldMissing"),
                transformMeta));
      }
      if (Utils.isEmpty(outputFileField)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "PentahoReportingOutputMeta.CheckResult.OutputFileFieldMissing"),
                transformMeta));
      }
    } else {
      if (Utils.isEmpty(inputFile)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "PentahoReportingOutputMeta.CheckResult.InputFileMissing"),
                transformMeta));
      }
      if (Utils.isEmpty(outputFile)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG, "PentahoReportingOutputMeta.CheckResult.OutputFileMissing"),
                transformMeta));
      }
    }
  }
}
