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

import java.awt.Window;
import java.util.Locale;
import lombok.Getter;
import org.pentaho.reporting.engine.classic.core.modules.gui.common.IconTheme;
import org.pentaho.reporting.engine.classic.core.modules.gui.common.StatusListener;
import org.pentaho.reporting.engine.classic.core.modules.gui.common.StatusType;
import org.pentaho.reporting.engine.classic.core.modules.gui.commonswing.ReportEventSource;
import org.pentaho.reporting.engine.classic.core.modules.gui.commonswing.SwingGuiContext;
import org.pentaho.reporting.libraries.base.config.Configuration;

/** Minimal SwingGuiContext used to collect export status from the classic engine. */
@Getter
public class PentahoReportingSwingGuiContext implements StatusListener, SwingGuiContext {

  private StatusType statusType;
  private String message;
  private Throwable cause;

  @Override
  public Configuration getConfiguration() {
    return null;
  }

  @Override
  public IconTheme getIconTheme() {
    return null;
  }

  @Override
  public Locale getLocale() {
    return Locale.getDefault();
  }

  @Override
  public ReportEventSource getEventSource() {
    return null;
  }

  @Override
  public StatusListener getStatusListener() {
    return this;
  }

  @Override
  public Window getWindow() {
    return null;
  }

  @Override
  public void setStatus(StatusType statusType, String message, Throwable cause) {
    this.statusType = statusType;
    this.message = message;
    this.cause = cause;
  }
}
