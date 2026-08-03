/*
 * Slim compile stub for Data Hopper PRD build.
 * Constants match pentaho-reporting 10.1 ReportDesignerParserModule.
 * Not a full classic engine module (no AbstractModule bootstrap).
 */
package org.pentaho.reporting.engine.classic.extensions.parsers.reportdesigner;

/**
 * Compile-time stand-in so report-designer can reference NAMESPACE and
 * guide-line attribute names without building the full parser (mondrian/pmd).
 */
public final class ReportDesignerParserModule {
  public static final String NAMESPACE =
      "http://reporting.pentaho.org/namespaces/report-designer/2.0";

  public static final String VERTICAL_GUIDE_LINES_ATTRIBUTE = "VerticalGuideLines";
  public static final String HORIZONTAL_GUIDE_LINES_ATTRIBUTE = "HorizontalGuideLines";
  public static final String HIDE_IN_LAYOUT_GUI_ATTRIBUTE = "hideInLayoutGUI";

  private ReportDesignerParserModule() {
  }
}
