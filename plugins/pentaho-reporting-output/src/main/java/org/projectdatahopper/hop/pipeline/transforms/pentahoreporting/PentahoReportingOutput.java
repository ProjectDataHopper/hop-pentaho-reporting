/*
 * Copyright (C) 2026 Project Data Hopper
 *
 * Ported from the historically Apache-2.0 licensed PDI Pentaho Reporting Output
 * step (pre-BSL header tree). Distributed under LGPL-2.1 with this plugin.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */

package org.projectdatahopper.hop.pipeline.transforms.pentahoreporting;

import java.awt.GraphicsEnvironment;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.provider.local.LocalFile;
import org.apache.hop.core.Const;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.projectdatahopper.hop.pipeline.transforms.pentahoreporting.urlrepository.FileObjectRepository;
import org.pentaho.reporting.engine.classic.core.ClassicEngineBoot;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.layout.output.ReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.gui.common.StatusType;
import org.pentaho.reporting.engine.classic.core.modules.output.fast.csv.FastCsvExportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.fast.html.FastHtmlContentItems;
import org.pentaho.reporting.engine.classic.core.modules.output.fast.html.FastHtmlExportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.fast.validator.ReportStructureValidator;
import org.pentaho.reporting.engine.classic.core.modules.output.fast.xls.FastExcelExportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.base.PageableReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.base.FlowReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.base.StreamReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.csv.StreamCSVOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.AllItemsHtmlPrinter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.FileSystemURLRewriter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.FlowHtmlOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlPrinter;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.StreamHtmlOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.rtf.StreamRTFOutputProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.FlowExcelOutputProcessor;
import org.pentaho.reporting.engine.classic.core.parameters.ParameterDefinitionEntry;
import org.pentaho.reporting.engine.classic.core.parameters.ReportParameterDefinition;
import org.pentaho.reporting.engine.classic.core.util.ReportParameterValues;
import org.pentaho.reporting.libraries.base.util.IOUtils;
import org.pentaho.reporting.libraries.base.util.ObjectUtilities;
import org.pentaho.reporting.libraries.fonts.LibFontBoot;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.DefaultNameGenerator;
import org.pentaho.reporting.libraries.resourceloader.LibLoaderBoot;
import org.pentaho.reporting.libraries.resourceloader.Resource;
import org.pentaho.reporting.libraries.resourceloader.ResourceException;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;

/**
 * Renders a Pentaho Reporting {@code .prpt} design file for each input row using the LGPL classic
 * engine.
 */
public class PentahoReportingOutput
    extends BaseTransform<PentahoReportingOutputMeta, PentahoReportingOutputData> {

  private static final Class<?> PKG = PentahoReportingOutput.class;

  public PentahoReportingOutput(
      TransformMeta transformMeta,
      PentahoReportingOutputMeta meta,
      PentahoReportingOutputData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);

    // Avoid macOS CGLGraphicsConfig hang when fonts/PDF path touches AWT
    if (Const.isOSX()) {
      GraphicsEnvironment.getLocalGraphicsEnvironment();
    }
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      setOutputDone();
      return false;
    }

    if (first) {
      first = false;
      resolveFieldIndexes();
    }

    performPentahoReportingBoot(getLogChannel(), getClass());

    String sourceFilename =
        meta.isUseValuesFromFields()
            ? getInputRowMeta().getString(row, data.inputFieldIndex)
            : resolve(meta.getInputFile());
    String targetFilename =
        meta.isUseValuesFromFields()
            ? getInputRowMeta().getString(row, data.outputFieldIndex)
            : resolve(meta.getOutputFile());

    processReport(
        row, sourceFilename, targetFilename, meta.getProcessorType(), meta.isCreateParentFolder());

    putRow(getInputRowMeta(), row);

    if (checkFeedback(getLinesWritten())) {
      logBasic(
          BaseMessages.getString(PKG, "PentahoReportingOutput.Log.LineNumber") + getLinesWritten());
    }

    return true;
  }

  private void resolveFieldIndexes() throws HopException {
    if (!meta.isUseValuesFromFields()) {
      if (Utils.isEmpty(meta.getInputFile())) {
        throw new HopException(
            BaseMessages.getString(PKG, "PentahoReportingOutput.Exception.InputFileMissing"));
      }
      if (Utils.isEmpty(meta.getOutputFile())) {
        throw new HopException(
            BaseMessages.getString(PKG, "PentahoReportingOutput.Exception.OutputFileMissing"));
      }
      return;
    }

    data.inputFieldIndex = getInputRowMeta().indexOfValue(meta.getInputFileField());
    if (data.inputFieldIndex < 0) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "PentahoReportingOutput.Exception.CanNotFindField", meta.getInputFileField()));
    }

    data.outputFieldIndex = getInputRowMeta().indexOfValue(meta.getOutputFileField());
    if (data.outputFieldIndex < 0) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "PentahoReportingOutput.Exception.CanNotFindField", meta.getOutputFileField()));
    }
  }

  public static void performPentahoReportingBoot(ILogChannel log, Class<?> referenceClass) {
    if (ClassicEngineBoot.getInstance().isBootDone()) {
      return;
    }

    ObjectUtilities.setClassLoader(referenceClass.getClassLoader());
    ObjectUtilities.setClassLoaderSource(ObjectUtilities.CLASS_CONTEXT);

    LibLoaderBoot.getInstance().start();
    LibFontBoot.getInstance().start();
    ClassicEngineBoot.getInstance().start();

    Exception exception = ClassicEngineBoot.getInstance().getBootFailureReason();
    if (exception != null) {
      log.logError("Error booting the Pentaho reporting engine", exception);
    }
  }

  public static MasterReport loadMasterReport(String sourceFilename, IVariables variables)
      throws HopFileException, MalformedURLException, ResourceException {
    ResourceManager manager = new ResourceManager();
    manager.registerDefaults();
    FileObject fileObject = getFileObject(sourceFilename, variables);
    Resource resource = manager.createDirectly(getKeyValue(fileObject), MasterReport.class);
    return (MasterReport) resource.getResource();
  }

  protected static FileObject getFileObject(String sourceFilename, IVariables variables)
      throws HopFileException {
    if (variables == null) {
      variables = new Variables();
      variables.initializeFrom(null);
    }
    return HopVfs.getFileObject(sourceFilename, variables);
  }

  protected static Object getKeyValue(FileObject fileObject) throws MalformedURLException {
    return fileObject instanceof LocalFile
        ? new URL(fileObject.getName().getURI())
        : fileObject;
  }

  void processReport(
      Object[] row,
      String sourceFilename,
      String targetFilename,
      ProcessorType outputProcessorType,
      boolean createParentFolder)
      throws HopException {

    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    try {
      // Ensure dynamic Class.forName() inside the engine uses the plugin classloader
      Thread.currentThread().setContextClassLoader(PentahoReportingOutput.class.getClassLoader());

      MasterReport report = loadMasterReport(sourceFilename, this);

      // Rewrite JNDI SQL providers → Hop DatabaseMeta before the engine queries
      ReportConnectionBinder.bind(
          report, meta, this, getMetadataProvider(), this, getLogChannel());

      bindParameters(report, row, sourceFilename);

      PentahoReportingSwingGuiContext context = new PentahoReportingSwingGuiContext();
      Runnable exportTask = createExportTask(report, context, targetFilename, createParentFolder, outputProcessorType);

      if (exportTask == null) {
        throw new HopTransformException(
            BaseMessages.getString(
                PKG,
                "PentahoReportingOutput.Exception.UnsupportedProcessor",
                outputProcessorType.getDescription()));
      }

      exportTask.run();

      if (context.getStatusType() == StatusType.ERROR) {
        try {
          HopVfs.getFileObject(targetFilename, this).delete();
        } catch (Exception ignored) {
          // best effort
        }
        if (context.getCause() != null) {
          throw new HopException(enrichJndiError(context.getMessage(), context.getCause()), context.getCause());
        }
        throw new HopTransformException(context.getMessage());
      }

      ResultFile resultFile =
          new ResultFile(
              ResultFile.FILE_TYPE_GENERAL,
              HopVfs.getFileObject(targetFilename, this),
              getPipelineMeta().getName(),
              getTransformName());
      resultFile.setComment("Created by Pentaho Reporting Output transform");
      addResultFile(resultFile);
    } catch (HopException e) {
      throw e;
    } catch (Throwable e) {
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "PentahoReportingOutput.Exception.UnexpectedErrorRenderingReport",
              sourceFilename,
              targetFilename,
              outputProcessorType.getDescription()),
          e);
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
    }
  }

  /**
   * When the engine still fails with a JNDI lookup error (unmapped datasource or
   * non-SQL path), append a short Hop-oriented hint.
   */
  static String enrichJndiError(String message, Throwable cause) {
    String base = message != null ? message : "Export failed";
    if (cause == null) {
      return base;
    }
    StringBuilder chain = new StringBuilder();
    Throwable t = cause;
    int depth = 0;
    while (t != null && depth < 12) {
      if (t.getMessage() != null) {
        chain.append(' ').append(t.getMessage());
      }
      t = t.getCause();
      depth++;
    }
    String lower = chain.toString().toLowerCase();
    if (lower.contains("jndi") || lower.contains("datasource")) {
      return base
          + Const.CR
          + BaseMessages.getString(PKG, "PentahoReportingOutput.Exception.JndiHint");
    }
    return base;
  }

  private void bindParameters(MasterReport report, Object[] row, String sourceFilename)
      throws HopException {
    ReportParameterValues values = report.getParameterValues();
    ReportParameterDefinition definition = report.getParameterDefinition();

    for (Map.Entry<String, String> entry : meta.getParameterFieldMap().entrySet()) {
      String parameterName = entry.getKey();
      String fieldName = entry.getValue();
      if (Utils.isEmpty(fieldName)) {
        continue;
      }

      int index = getInputRowMeta().indexOfValue(fieldName);
      if (index < 0) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "PentahoReportingOutput.Exception.CanNotFindField", fieldName));
      }

      Class<?> clazz = findParameterClass(definition, parameterName);
      if (clazz == null) {
        logBasic(
            BaseMessages.getString(
                PKG,
                "PentahoReportingOutput.Log.ParameterNotFoundInReport",
                parameterName,
                sourceFilename));
        continue;
      }

      Object value;
      if (clazz.equals(String.class)) {
        value = getInputRowMeta().getString(row, index);
      } else if (clazz.equals(String[].class)) {
        value = getInputRowMeta().getString(row, index).split("\t");
      } else if (clazz.equals(Date.class)) {
        value = getInputRowMeta().getDate(row, index);
      } else if (clazz.equals(Byte.class) || clazz.equals(byte.class)) {
        value = getInputRowMeta().getInteger(row, index).byteValue();
      } else if (clazz.equals(Short.class) || clazz.equals(short.class)) {
        value = getInputRowMeta().getInteger(row, index).shortValue();
      } else if (clazz.equals(Integer.class) || clazz.equals(int.class)) {
        value = getInputRowMeta().getInteger(row, index).intValue();
      } else if (clazz.equals(Long.class) || clazz.equals(long.class)) {
        value = getInputRowMeta().getInteger(row, index);
      } else if (clazz.equals(Double.class) || clazz.equals(double.class)) {
        value = getInputRowMeta().getNumber(row, index);
      } else if (clazz.equals(Float.class) || clazz.equals(float.class)) {
        value = getInputRowMeta().getNumber(row, index).floatValue();
      } else if (clazz.equals(Number.class)) {
        value = getInputRowMeta().getBigNumber(row, index).floatValue();
      } else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
        value = getInputRowMeta().getBoolean(row, index);
      } else if (clazz.equals(BigDecimal.class)) {
        value = getInputRowMeta().getBigNumber(row, index);
      } else if (clazz.equals(byte[].class)) {
        value = getInputRowMeta().getBinary(row, index);
      } else {
        value = getInputRowMeta().getValueMeta(index).convertToNormalStorageType(row[index]);
      }

      values.put(parameterName, value);
    }
  }

  private Runnable createExportTask(
      MasterReport report,
      PentahoReportingSwingGuiContext context,
      String targetFilename,
      boolean createParentFolder,
      ProcessorType outputProcessorType) {

    return switch (outputProcessorType) {
      case PDF ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              PdfOutputProcessor outputProcessor =
                  new PdfOutputProcessor(
                      report.getConfiguration(), fout, report.getResourceManager());
              return new PageableReportProcessor(report, outputProcessor);
            }
          };
      case CSV ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              ReportStructureValidator validator = new ReportStructureValidator();
              if (!validator.isValidForFastProcessing(report)) {
                return new StreamReportProcessor(report, new StreamCSVOutputProcessor(fout));
              }
              return new FastCsvExportProcessor(report, fout);
            }
          };
      case Excel ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              ReportStructureValidator validator = new ReportStructureValidator();
              if (!validator.isValidForFastProcessing(report)) {
                FlowExcelOutputProcessor target =
                    new FlowExcelOutputProcessor(
                        report.getConfiguration(), fout, report.getResourceManager());
                target.setUseXlsxFormat(false);
                return new FlowReportProcessor(report, target);
              }
              return new FastExcelExportProcessor(report, fout, false);
            }
          };
      case Excel_2007 ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              ReportStructureValidator validator = new ReportStructureValidator();
              if (!validator.isValidForFastProcessing(report)) {
                FlowExcelOutputProcessor target =
                    new FlowExcelOutputProcessor(
                        report.getConfiguration(), fout, report.getResourceManager());
                target.setUseXlsxFormat(true);
                return new FlowReportProcessor(report, target);
              }
              return new FastExcelExportProcessor(report, fout, true);
            }
          };
      case StreamingHTML ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            private String filename;
            private String suffix;
            private ContentLocation targetRoot;

            @Override
            protected void execute() throws Exception {
              FileObject targetDirectory = requireTargetFile().getParent();
              FileObjectRepository targetRepository = new FileObjectRepository(targetDirectory);
              targetRoot = targetRepository.getRoot();
              suffix = getSuffix(targetPath);
              filename =
                  IOUtils.getInstance()
                      .stripFileExtension(requireTargetFile().getName().getBaseName());

              ReportProcessor reportProcessor = createReportProcessor(null);
              try {
                reportProcessor.processReport();
              } finally {
                reportProcessor.close();
              }
              statusListener.setStatus(
                  StatusType.INFORMATION,
                  BaseMessages.getString(ReportExportTask.class, "ReportExportTask.USER_EXPORT_COMPLETE"),
                  null);
            }

            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              ReportStructureValidator validator = new ReportStructureValidator();
              if (!validator.isValidForFastProcessing(report)) {
                HtmlOutputProcessor outputProcessor =
                    new StreamHtmlOutputProcessor(report.getConfiguration());
                HtmlPrinter printer = new AllItemsHtmlPrinter(report.getResourceManager());
                printer.setContentWriter(
                    targetRoot, new DefaultNameGenerator(targetRoot, filename, suffix));
                printer.setDataWriter(null, null);
                printer.setUrlRewriter(new FileSystemURLRewriter());
                outputProcessor.setPrinter(printer);
                return new StreamReportProcessor(report, outputProcessor);
              }
              FastHtmlContentItems printer = new FastHtmlContentItems();
              printer.setContentWriter(
                  targetRoot, new DefaultNameGenerator(targetRoot, filename, suffix));
              printer.setDataWriter(null, null);
              printer.setUrlRewriter(new FileSystemURLRewriter());
              return new FastHtmlExportProcessor(report, printer);
            }
          };
      case PagedHTML ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            private String filename;
            private String suffix;
            private ContentLocation targetRoot;

            @Override
            protected void execute() throws Exception {
              FileObject targetDirectory = requireTargetFile().getParent();
              FileObjectRepository targetRepository = new FileObjectRepository(targetDirectory);
              targetRoot = targetRepository.getRoot();
              suffix = getSuffix(targetPath);
              Path p = Paths.get(requireTargetFile().getName().getPath());
              filename = IOUtils.getInstance().stripFileExtension(p.getFileName().toString());

              ReportProcessor reportProcessor = createReportProcessor(null);
              try {
                reportProcessor.processReport();
              } finally {
                reportProcessor.close();
              }
              statusListener.setStatus(
                  StatusType.INFORMATION,
                  BaseMessages.getString(ReportExportTask.class, "ReportExportTask.USER_EXPORT_COMPLETE"),
                  null);
            }

            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              FlowHtmlOutputProcessor outputProcessor = new FlowHtmlOutputProcessor();
              HtmlPrinter printer = new AllItemsHtmlPrinter(report.getResourceManager());
              printer.setContentWriter(
                  targetRoot, new DefaultNameGenerator(targetRoot, filename, suffix));
              printer.setDataWriter(targetRoot, new DefaultNameGenerator(targetRoot, "content"));
              printer.setUrlRewriter(new FileSystemURLRewriter());
              outputProcessor.setPrinter(printer);
              return new FlowReportProcessor(report, outputProcessor);
            }
          };
      case RTF ->
          new ReportExportTask(report, context, targetFilename, createParentFolder, this) {
            @Override
            protected ReportProcessor createReportProcessor(OutputStream fout) throws Exception {
              StreamRTFOutputProcessor target =
                  new StreamRTFOutputProcessor(
                      report.getConfiguration(), fout, report.getResourceManager());
              return new StreamReportProcessor(report, target);
            }
          };
    };
  }

  private static Class<?> findParameterClass(
      ReportParameterDefinition definition, String parameterName) {
    for (int i = 0; i < definition.getParameterCount(); i++) {
      ParameterDefinitionEntry entry = definition.getParameterDefinition(i);
      if (parameterName.equals(entry.getName())) {
        return entry.getValueType();
      }
    }
    return null;
  }
}
