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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.ConnectionProvider;

/**
 * Classic-engine {@link ConnectionProvider} that opens JDBC connections through
 * Hop {@link Database} / {@link DatabaseMeta}, so driver plugins, encrypted
 * passwords, connect SQL and extra options behave like Table Input.
 *
 * <p>Each call to {@link #createConnection(String, String)} returns a fresh
 * connection (the reporting engine may close it after use).
 */
public class HopDatabaseConnectionProvider implements ConnectionProvider {
  private static final long serialVersionUID = 1L;

  private final String connectionName;
  private final transient DatabaseMeta databaseMeta;
  private final transient IVariables variables;
  private final transient ILoggingObject parent;

  public HopDatabaseConnectionProvider(
      DatabaseMeta databaseMeta, IVariables variables, ILoggingObject parent) {
    if (databaseMeta == null) {
      throw new NullPointerException("databaseMeta");
    }
    this.databaseMeta = databaseMeta;
    this.connectionName = databaseMeta.getName();
    this.variables = variables;
    this.parent =
        parent != null
            ? parent
            : new SimpleLoggingObject(
                "PentahoReportingOutput", LoggingObjectType.GENERAL, null);
  }

  @Override
  public Connection createConnection(String user, String password) throws SQLException {
    // Report-level user/password fields are rare for JNDI-migrated reports; Hop
    // metadata credentials are the source of truth. Override only when both
    // provided by the engine call.
    DatabaseMeta metaToUse = databaseMeta;
    if (!Utils.isEmpty(user) || !Utils.isEmpty(password)) {
      metaToUse = (DatabaseMeta) databaseMeta.clone();
      if (!Utils.isEmpty(user)) {
        metaToUse.setUsername(user);
      }
      if (!Utils.isEmpty(password)) {
        metaToUse.setPassword(password);
      }
    }

    Database database = new Database(parent, variables, metaToUse);
    try {
      database.connect();
      Connection connection = database.getConnection();
      if (connection == null) {
        throw new SQLException(
            "Hop Database returned no JDBC connection for '" + connectionName + "'");
      }
      // Detach so Database GC does not close the connection the engine owns.
      // The engine closes the Connection when done.
      return connection;
    } catch (HopDatabaseException e) {
      try {
        database.disconnect();
      } catch (Exception ignored) {
        // best effort
      }
      throw new SQLException(
          "Failed to open Hop connection '" + connectionName + "': " + e.getMessage(), e);
    }
  }

  @Override
  public Object getConnectionHash() {
    List<Object> hash = new ArrayList<>(3);
    hash.add(getClass().getName());
    hash.add(connectionName);
    try {
      if (databaseMeta != null && variables != null) {
        hash.add(databaseMeta.getURL(variables));
      }
    } catch (Exception e) {
      hash.add(connectionName);
    }
    return hash;
  }

  public String getConnectionName() {
    return connectionName;
  }
}
