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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.pentaho.reporting.engine.classic.core.AbstractReportDefinition;
import org.pentaho.reporting.engine.classic.core.CompoundDataFactory;
import org.pentaho.reporting.engine.classic.core.DataFactory;
import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.ReportElement;
import org.pentaho.reporting.engine.classic.core.RootLevelBand;
import org.pentaho.reporting.engine.classic.core.Section;
import org.pentaho.reporting.engine.classic.core.SubReport;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.ConnectionProvider;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.JndiConnectionProvider;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.SimpleSQLReportDataFactory;

/**
 * Rewrites JNDI-based SQL connection providers on a loaded {@link MasterReport}
 * to Hop-backed providers so reports designed for PDI/PRD work without a JNDI
 * environment.
 *
 * <p>Resolution order for each JNDI name:
 *
 * <ol>
 *   <li>Explicit mapping in transform metadata
 *   <li>Same-name Hop connection (when enabled)
 *   <li>Unbound (fail or warn per meta)
 * </ol>
 */
public final class ReportConnectionBinder {

  private static final Class<?> PKG = ReportConnectionBinder.class;

  private ReportConnectionBinder() {}

  /**
   * Walk the report (and subreports) and replace {@link JndiConnectionProvider}
   * instances with {@link HopDatabaseConnectionProvider}.
   *
   * @return number of factories rewritten
   */
  public static int bind(
      MasterReport report,
      PentahoReportingOutputMeta meta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILoggingObject loggingObject,
      ILogChannel log)
      throws HopException {

    if (report == null) {
      return 0;
    }

    Map<String, String> explicitMap = buildExplicitMap(meta);
    Set<String> unbound = new LinkedHashSet<>();
    Set<String> bound = new LinkedHashSet<>();
    int rewritten = bindReportDefinition(
        report, meta, variables, metadataProvider, loggingObject, log, explicitMap, unbound, bound);

    if (!unbound.isEmpty() && meta.isFailIfUnboundJndi()) {
      List<String> available = listAvailableConnections(metadataProvider);
      throw new HopException(
          BaseMessages.getString(
              PKG,
              "PentahoReportingOutput.Exception.UnboundJndiDatasources",
              String.join(", ", unbound),
              available.isEmpty() ? "(none)" : String.join(", ", available)));
    }

    if (!unbound.isEmpty() && log != null && log.isBasic()) {
      log.logBasic(
          BaseMessages.getString(
              PKG,
              "PentahoReportingOutput.Log.UnboundJndiDatasources",
              String.join(", ", unbound)));
    }

    if (log != null && log.isDetailed() && !bound.isEmpty()) {
      log.logDetailed(
          BaseMessages.getString(
              PKG,
              "PentahoReportingOutput.Log.BoundJndiDatasources",
              String.join(", ", bound)));
    }

    return rewritten;
  }

  private static Map<String, String> buildExplicitMap(PentahoReportingOutputMeta meta) {
    Map<String, String> map = new LinkedHashMap<>();
    if (meta.getConnectionMappings() == null) {
      return map;
    }
    for (ReportConnectionMapping mapping : meta.getConnectionMappings()) {
      if (mapping == null) {
        continue;
      }
      String jndi = mapping.getJndiName();
      String hop = mapping.getHopConnectionName();
      if (!Utils.isEmpty(jndi) && !Utils.isEmpty(hop)) {
        map.put(jndi.trim(), hop.trim());
      }
    }
    return map;
  }

  private static int bindReportDefinition(
      AbstractReportDefinition reportDefinition,
      PentahoReportingOutputMeta meta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILoggingObject loggingObject,
      ILogChannel log,
      Map<String, String> explicitMap,
      Set<String> unbound,
      Set<String> bound)
      throws HopException {

    int rewritten =
        bindDataFactory(
            reportDefinition.getDataFactory(),
            meta,
            variables,
            metadataProvider,
            loggingObject,
            log,
            explicitMap,
            unbound,
            bound);

    rewritten +=
        traverseSection(
            reportDefinition,
            meta,
            variables,
            metadataProvider,
            loggingObject,
            log,
            explicitMap,
            unbound,
            bound);
    return rewritten;
  }

  private static int traverseSection(
      Section section,
      PentahoReportingOutputMeta meta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILoggingObject loggingObject,
      ILogChannel log,
      Map<String, String> explicitMap,
      Set<String> unbound,
      Set<String> bound)
      throws HopException {

    int rewritten = 0;
    final int count = section.getElementCount();
    for (int i = 0; i < count; i++) {
      ReportElement element = section.getElement(i);
      if (element instanceof SubReport) {
        rewritten +=
            bindReportDefinition(
                (SubReport) element,
                meta,
                variables,
                metadataProvider,
                loggingObject,
                log,
                explicitMap,
                unbound,
                bound);
      } else if (element instanceof Section) {
        rewritten +=
            traverseSection(
                (Section) element,
                meta,
                variables,
                metadataProvider,
                loggingObject,
                log,
                explicitMap,
                unbound,
                bound);
        if (element instanceof RootLevelBand) {
          RootLevelBand rlb = (RootLevelBand) element;
          for (int sr = 0; sr < rlb.getSubReportCount(); sr++) {
            rewritten +=
                bindReportDefinition(
                    rlb.getSubReport(sr),
                    meta,
                    variables,
                    metadataProvider,
                    loggingObject,
                    log,
                    explicitMap,
                    unbound,
                    bound);
          }
        }
      }
    }
    return rewritten;
  }

  private static int bindDataFactory(
      DataFactory dataFactory,
      PentahoReportingOutputMeta meta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILoggingObject loggingObject,
      ILogChannel log,
      Map<String, String> explicitMap,
      Set<String> unbound,
      Set<String> bound)
      throws HopException {

    if (dataFactory == null) {
      return 0;
    }

    int rewritten = 0;

    // Flatten nested compounds without cloning children (derive=false).
    CompoundDataFactory compound = CompoundDataFactory.normalize(dataFactory, false);
    for (int i = 0; i < compound.size(); i++) {
      DataFactory child = compound.getReference(i);
      if (child instanceof CompoundDataFactory) {
        rewritten +=
            bindDataFactory(
                child,
                meta,
                variables,
                metadataProvider,
                loggingObject,
                log,
                explicitMap,
                unbound,
                bound);
        continue;
      }
      if (child instanceof SimpleSQLReportDataFactory) {
        if (rewriteSqlFactory(
            (SimpleSQLReportDataFactory) child,
            meta,
            variables,
            metadataProvider,
            loggingObject,
            log,
            explicitMap,
            unbound,
            bound)) {
          rewritten++;
        }
      } else if (log != null && log.isDebug()) {
        log.logDebug(
            "Skipping non-SQL data factory while binding connections: "
                + child.getClass().getName());
      }
    }
    return rewritten;
  }

  private static boolean rewriteSqlFactory(
      SimpleSQLReportDataFactory sqlFactory,
      PentahoReportingOutputMeta meta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      ILoggingObject loggingObject,
      ILogChannel log,
      Map<String, String> explicitMap,
      Set<String> unbound,
      Set<String> bound)
      throws HopException {

    ConnectionProvider provider = sqlFactory.getConnectionProvider();
    if (!(provider instanceof JndiConnectionProvider)) {
      return false;
    }

    JndiConnectionProvider jndi = (JndiConnectionProvider) provider;
    String jndiName = jndi.getConnectionPath();
    if (Utils.isEmpty(jndiName)) {
      unbound.add("(empty JNDI path)");
      return false;
    }

    // Strip common prefixes if present (java:comp/env/jdbc/SampleData → SampleData)
    String lookupKey = normalizeJndiName(jndiName);

    String hopConnectionName = explicitMap.get(lookupKey);
    if (Utils.isEmpty(hopConnectionName) && explicitMap.containsKey(jndiName)) {
      hopConnectionName = explicitMap.get(jndiName);
    }

    if (Utils.isEmpty(hopConnectionName) && meta.isUseSameNameHopConnections()) {
      hopConnectionName = lookupKey;
    }

    if (Utils.isEmpty(hopConnectionName)) {
      unbound.add(jndiName);
      return false;
    }

    DatabaseMeta databaseMeta = loadDatabaseMeta(metadataProvider, variables, hopConnectionName);
    if (databaseMeta == null) {
      // Explicit map pointed at missing connection, or same-name miss
      if (explicitMap.containsKey(lookupKey) || explicitMap.containsKey(jndiName)) {
        throw new HopException(
            BaseMessages.getString(
                PKG,
                "PentahoReportingOutput.Exception.HopConnectionNotFound",
                hopConnectionName,
                jndiName));
      }
      unbound.add(jndiName);
      return false;
    }

    HopDatabaseConnectionProvider hopProvider =
        new HopDatabaseConnectionProvider(databaseMeta, variables, loggingObject);
    sqlFactory.setConnectionProvider(hopProvider);
    bound.add(jndiName + " → " + databaseMeta.getName());
    if (log != null && log.isDetailed()) {
      log.logDetailed(
          "Bound report JNDI datasource '"
              + jndiName
              + "' to Hop connection '"
              + databaseMeta.getName()
              + "'");
    }
    return true;
  }

  /**
   * Normalize JNDI paths used by PRD/PDI (e.g. {@code java:comp/env/jdbc/SampleData}
   * or {@code jdbc/SampleData}) down to the leaf name for Hop mapping.
   */
  static String normalizeJndiName(String jndiName) {
    if (jndiName == null) {
      return null;
    }
    String name = jndiName.trim();
    String lower = name.toLowerCase();
    if (lower.startsWith("java:comp/env/")) {
      name = name.substring("java:comp/env/".length());
    }
    if (name.regionMatches(true, 0, "jdbc/", 0, 5)) {
      name = name.substring(5);
    }
    // Keep last path segment if still hierarchical
    int slash = name.lastIndexOf('/');
    if (slash >= 0 && slash < name.length() - 1) {
      name = name.substring(slash + 1);
    }
    return name;
  }

  private static DatabaseMeta loadDatabaseMeta(
      IHopMetadataProvider metadataProvider, IVariables variables, String connectionName)
      throws HopException {
    if (metadataProvider == null || Utils.isEmpty(connectionName)) {
      return null;
    }
    String resolved = variables != null ? variables.resolve(connectionName) : connectionName;
    var serializer = metadataProvider.getSerializer(DatabaseMeta.class);
    if (!serializer.exists(resolved)) {
      return null;
    }
    return serializer.load(resolved);
  }

  private static List<String> listAvailableConnections(IHopMetadataProvider metadataProvider) {
    List<String> names = new ArrayList<>();
    if (metadataProvider == null) {
      return names;
    }
    try {
      names.addAll(metadataProvider.getSerializer(DatabaseMeta.class).listObjectNames());
    } catch (Exception ignored) {
      // best effort for error messages
    }
    return names;
  }
}
