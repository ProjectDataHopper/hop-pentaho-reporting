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

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.ReportProcessingException;
import org.pentaho.reporting.engine.classic.core.layout.output.ReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.gui.common.StatusListener;
import org.pentaho.reporting.engine.classic.core.modules.gui.common.StatusType;
import org.pentaho.reporting.engine.classic.core.modules.gui.commonswing.SwingGuiContext;
import org.pentaho.reporting.libraries.base.util.IOUtils;

/** Exports a {@link MasterReport} to a target file via Hop VFS. */
public abstract class ReportExportTask implements Runnable {
  private static final Class<?> PKG = ReportExportTask.class;

  protected final MasterReport report;
  protected final StatusListener statusListener;
  protected final boolean createParentFolder;
  protected final String targetPath;
  protected final IVariables variables;
  protected FileObject targetFile;

  protected ReportExportTask(
      MasterReport report,
      SwingGuiContext swingGuiContext,
      String targetPath,
      boolean createParentFolder,
      IVariables variables) {
    if (report == null) {
      throw new NullPointerException("Report parameter cannot be null");
    }
    this.report = report;
    this.statusListener = swingGuiContext.getStatusListener();
    this.targetPath = targetPath;
    this.createParentFolder = createParentFolder;
    this.variables = variables;
  }

  @Override
  public void run() {
    try {
      targetFile = HopVfs.getFileObject(targetPath, variables);
      if (targetFile.exists() && !targetFile.delete()) {
        throw new ReportProcessingException(
            BaseMessages.getString(PKG, "ReportExportTask.ERROR_0001_TARGET_EXISTS"));
      }

      if (createParentFolder) {
        if (targetFile.getParent() != null && !targetFile.getParent().exists()) {
          targetFile.getParent().createFolder();
        }
      } else if (targetFile.getParent() != null && !targetFile.getParent().exists()) {
        throw new ReportProcessingException(
            BaseMessages.getString(
                PKG,
                "ReportExportTask.PARENT_FOLDER_DOES_NOT_EXIST",
                targetFile.getParent().getName().getPath()));
      }

      execute();
    } catch (Exception ex) {
      statusListener.setStatus(
          StatusType.ERROR,
          BaseMessages.getString(PKG, "ReportExportTask.USER_EXPORT_FAILED"),
          ex);
    }
  }

  protected void execute() throws Exception {
    BufferedOutputStream fout = null;
    ReportProcessor reportProcessor = null;
    try {
      fout = new BufferedOutputStream(HopVfs.getOutputStream(targetFile, false));
      reportProcessor = createReportProcessor(fout);
      reportProcessor.processReport();
      statusListener.setStatus(
          StatusType.INFORMATION,
          BaseMessages.getString(PKG, "ReportExportTask.USER_EXPORT_COMPLETE"),
          null);
      reportProcessor.close();
      fout.close();
      fout = null;
    } catch (Exception ex) {
      if (reportProcessor != null) {
        try {
          reportProcessor.close();
        } catch (Exception ignored) {
          // ignore close failures after primary error
        }
      }
      if (fout != null) {
        try {
          fout.close();
        } catch (Exception ignored) {
          // ignore
        }
      }
      try {
        if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
          // incomplete export left behind
        }
      } catch (Exception ignored) {
        // ignore
      }
      throw ex;
    }
  }

  protected abstract ReportProcessor createReportProcessor(OutputStream fout) throws Exception;

  protected String getSuffix(String filename) {
    String suffix = IOUtils.getInstance().getFileExtension(filename);
    if (suffix == null || suffix.isEmpty()) {
      return "";
    }
    return suffix.startsWith(".") ? suffix.substring(1) : suffix;
  }

  protected FileObject requireTargetFile() throws HopFileException {
    if (targetFile == null) {
      targetFile = HopVfs.getFileObject(targetPath, variables);
    }
    return targetFile;
  }
}
