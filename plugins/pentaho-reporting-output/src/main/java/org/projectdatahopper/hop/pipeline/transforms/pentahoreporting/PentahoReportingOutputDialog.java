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
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

/** Configuration dialog for {@link PentahoReportingOutputMeta}. */
public class PentahoReportingOutputDialog extends BaseTransformDialog {

  private static final Class<?> PKG = PentahoReportingOutputDialog.class;

  private final PentahoReportingOutputMeta input;

  private Button wUseValuesFromFields;
  private ComboVar wInputField;
  private ComboVar wOutputField;
  private TextVar wInputFile;
  private TextVar wOutputFile;
  private CCombo wProcessor;
  private Button wCreateParentFolder;
  private Button wUseSameNameHopConnections;
  private Button wFailIfUnboundJndi;
  private TableView wConnectionMappings;
  private TableView wParameters;

  private boolean gotPreviousFields;

  public PentahoReportingOutputDialog(
      Shell parent,
      IVariables variables,
      PentahoReportingOutputMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    this.input = transformMeta;
  }

  @Override
  public String open() {
    Control lastControl =
        createShell(BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Shell.Title"));

    ModifyListener lsMod = e -> input.setChanged();
    SelectionAdapter lsSel =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            input.setChanged();
            setFlags();
          }
        };

    // Use values from fields
    wUseValuesFromFields = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wUseValuesFromFields);
    wUseValuesFromFields.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.UseValuesFromFields.Label"));
    wUseValuesFromFields.setToolTipText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.UseValuesFromFields.Tooltip"));
    FormData fdUse = new FormData();
    fdUse.left = new FormAttachment(middle, 0);
    fdUse.top = new FormAttachment(lastControl, margin);
    fdUse.right = new FormAttachment(100, 0);
    wUseValuesFromFields.setLayoutData(fdUse);
    wUseValuesFromFields.addSelectionListener(lsSel);
    lastControl = wUseValuesFromFields;

    // Input file field
    Label wlInputField = new Label(shell, SWT.RIGHT);
    wlInputField.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.InputFileField.Label"));
    PropsUi.setLook(wlInputField);
    FormData fdlInputField = new FormData();
    fdlInputField.left = new FormAttachment(0, 0);
    fdlInputField.top = new FormAttachment(lastControl, margin);
    fdlInputField.right = new FormAttachment(middle, -margin);
    wlInputField.setLayoutData(fdlInputField);

    // Editable combo: saved field names must still show when not yet in the stream list
    wInputField = new ComboVar(variables, shell, SWT.BORDER);
    PropsUi.setLook(wInputField);
    wInputField.addModifyListener(lsMod);
    FormData fdInputField = new FormData();
    fdInputField.left = new FormAttachment(middle, 0);
    fdInputField.top = new FormAttachment(lastControl, margin);
    fdInputField.right = new FormAttachment(100, 0);
    wInputField.setLayoutData(fdInputField);
    lastControl = wInputField;

    // Output file field
    Label wlOutputField = new Label(shell, SWT.RIGHT);
    wlOutputField.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.OutputFileField.Label"));
    PropsUi.setLook(wlOutputField);
    FormData fdlOutputField = new FormData();
    fdlOutputField.left = new FormAttachment(0, 0);
    fdlOutputField.top = new FormAttachment(lastControl, margin);
    fdlOutputField.right = new FormAttachment(middle, -margin);
    wlOutputField.setLayoutData(fdlOutputField);

    wOutputField = new ComboVar(variables, shell, SWT.BORDER);
    PropsUi.setLook(wOutputField);
    wOutputField.addModifyListener(lsMod);
    FormData fdOutputField = new FormData();
    fdOutputField.left = new FormAttachment(middle, 0);
    fdOutputField.top = new FormAttachment(lastControl, margin);
    fdOutputField.right = new FormAttachment(100, 0);
    wOutputField.setLayoutData(fdOutputField);
    lastControl = wOutputField;

    // Static input file
    Label wlInputFile = new Label(shell, SWT.RIGHT);
    wlInputFile.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.InputFile.Label"));
    PropsUi.setLook(wlInputFile);
    FormData fdlInputFile = new FormData();
    fdlInputFile.left = new FormAttachment(0, 0);
    fdlInputFile.top = new FormAttachment(lastControl, margin);
    fdlInputFile.right = new FormAttachment(middle, -margin);
    wlInputFile.setLayoutData(fdlInputFile);

    wInputFile = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wInputFile);
    wInputFile.addModifyListener(lsMod);
    FormData fdInputFile = new FormData();
    fdInputFile.left = new FormAttachment(middle, 0);
    fdInputFile.top = new FormAttachment(lastControl, margin);
    fdInputFile.right = new FormAttachment(100, 0);
    wInputFile.setLayoutData(fdInputFile);
    lastControl = wInputFile;

    // Static output file
    Label wlOutputFile = new Label(shell, SWT.RIGHT);
    wlOutputFile.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.OutputFile.Label"));
    PropsUi.setLook(wlOutputFile);
    FormData fdlOutputFile = new FormData();
    fdlOutputFile.left = new FormAttachment(0, 0);
    fdlOutputFile.top = new FormAttachment(lastControl, margin);
    fdlOutputFile.right = new FormAttachment(middle, -margin);
    wlOutputFile.setLayoutData(fdlOutputFile);

    wOutputFile = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wOutputFile);
    wOutputFile.addModifyListener(lsMod);
    FormData fdOutputFile = new FormData();
    fdOutputFile.left = new FormAttachment(middle, 0);
    fdOutputFile.top = new FormAttachment(lastControl, margin);
    fdOutputFile.right = new FormAttachment(100, 0);
    wOutputFile.setLayoutData(fdOutputFile);
    lastControl = wOutputFile;

    // Processor type
    Label wlProcessor = new Label(shell, SWT.RIGHT);
    wlProcessor.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Processor.Label"));
    PropsUi.setLook(wlProcessor);
    FormData fdlProcessor = new FormData();
    fdlProcessor.left = new FormAttachment(0, 0);
    fdlProcessor.top = new FormAttachment(lastControl, margin);
    fdlProcessor.right = new FormAttachment(middle, -margin);
    wlProcessor.setLayoutData(fdlProcessor);

    wProcessor = new CCombo(shell, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wProcessor);
    wProcessor.setItems(ProcessorType.getDescriptions());
    wProcessor.addModifyListener(lsMod);
    FormData fdProcessor = new FormData();
    fdProcessor.left = new FormAttachment(middle, 0);
    fdProcessor.top = new FormAttachment(lastControl, margin);
    fdProcessor.right = new FormAttachment(100, 0);
    wProcessor.setLayoutData(fdProcessor);
    lastControl = wProcessor;

    // Create parent folder
    wCreateParentFolder = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wCreateParentFolder);
    wCreateParentFolder.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.CreateParentFolder.Label"));
    FormData fdParent = new FormData();
    fdParent.left = new FormAttachment(middle, 0);
    fdParent.top = new FormAttachment(lastControl, margin);
    fdParent.right = new FormAttachment(100, 0);
    wCreateParentFolder.setLayoutData(fdParent);
    wCreateParentFolder.addSelectionListener(lsSel);
    lastControl = wCreateParentFolder;

    // Same-name Hop connections for JNDI
    wUseSameNameHopConnections = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wUseSameNameHopConnections);
    wUseSameNameHopConnections.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.UseSameNameHopConnections.Label"));
    wUseSameNameHopConnections.setToolTipText(
        BaseMessages.getString(
            PKG, "PentahoReportingOutputDialog.UseSameNameHopConnections.Tooltip"));
    FormData fdSameName = new FormData();
    fdSameName.left = new FormAttachment(middle, 0);
    fdSameName.top = new FormAttachment(lastControl, margin);
    fdSameName.right = new FormAttachment(100, 0);
    wUseSameNameHopConnections.setLayoutData(fdSameName);
    wUseSameNameHopConnections.addSelectionListener(lsSel);
    lastControl = wUseSameNameHopConnections;

    // Fail if unbound JNDI
    wFailIfUnboundJndi = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wFailIfUnboundJndi);
    wFailIfUnboundJndi.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.FailIfUnboundJndi.Label"));
    wFailIfUnboundJndi.setToolTipText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.FailIfUnboundJndi.Tooltip"));
    FormData fdFailJndi = new FormData();
    fdFailJndi.left = new FormAttachment(middle, 0);
    fdFailJndi.top = new FormAttachment(lastControl, margin);
    fdFailJndi.right = new FormAttachment(100, 0);
    wFailIfUnboundJndi.setLayoutData(fdFailJndi);
    wFailIfUnboundJndi.addSelectionListener(lsSel);
    lastControl = wFailIfUnboundJndi;

    // JNDI → Hop connection mappings
    Label wlConnections = new Label(shell, SWT.RIGHT);
    wlConnections.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.ConnectionMappings.Label"));
    PropsUi.setLook(wlConnections);
    FormData fdlConnections = new FormData();
    fdlConnections.left = new FormAttachment(0, 0);
    fdlConnections.top = new FormAttachment(lastControl, margin * 2);
    fdlConnections.right = new FormAttachment(middle, -margin);
    wlConnections.setLayoutData(fdlConnections);

    String[] hopConnectionNames = loadHopConnectionNames();
    ColumnInfo[] connectionColumns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Column.JndiName"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Column.HopConnection"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              hopConnectionNames,
              false)
        };

    wConnectionMappings =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            connectionColumns,
            input.getConnectionMappings() != null ? input.getConnectionMappings().size() : 1,
            false,
            lsMod,
            props);
    FormData fdConnections = new FormData();
    fdConnections.left = new FormAttachment(0, 0);
    fdConnections.top = new FormAttachment(wlConnections, margin);
    fdConnections.right = new FormAttachment(100, 0);
    fdConnections.bottom = new FormAttachment(55, 0);
    wConnectionMappings.setLayoutData(fdConnections);

    // Parameters table
    Label wlParameters = new Label(shell, SWT.RIGHT);
    wlParameters.setText(
        BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Parameters.Label"));
    PropsUi.setLook(wlParameters);
    FormData fdlParameters = new FormData();
    fdlParameters.left = new FormAttachment(0, 0);
    fdlParameters.top = new FormAttachment(wConnectionMappings, margin * 2);
    fdlParameters.right = new FormAttachment(middle, -margin);
    wlParameters.setLayoutData(fdlParameters);

    ColumnInfo[] columns =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Column.Parameter"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "PentahoReportingOutputDialog.Column.Field"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              new String[] {""},
              false)
        };

    wParameters =
        new TableView(
            variables,
            shell,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            columns,
            input.getParameters() != null ? input.getParameters().size() : 1,
            false,
            lsMod,
            props);
    FormData fdParameters = new FormData();
    fdParameters.left = new FormAttachment(0, 0);
    fdParameters.top = new FormAttachment(wlParameters, margin);
    fdParameters.right = new FormAttachment(100, 0);
    fdParameters.bottom = new FormAttachment(100, -50);
    wParameters.setLayoutData(fdParameters);

    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  private void getData() {
    wTransformName.setText(Const.NVL(transformName, ""));

    // Populate combo item lists BEFORE setText — SWT CCombo.setItems() clears the current text.
    populatePreviousFields();

    wUseValuesFromFields.setSelection(input.isUseValuesFromFields());
    wInputField.setText(Const.NVL(input.getInputFileField(), ""));
    wOutputField.setText(Const.NVL(input.getOutputFileField(), ""));
    wInputFile.setText(Const.NVL(input.getInputFile(), ""));
    wOutputFile.setText(Const.NVL(input.getOutputFile(), ""));
    wCreateParentFolder.setSelection(input.isCreateParentFolder());
    wUseSameNameHopConnections.setSelection(input.isUseSameNameHopConnections());
    wFailIfUnboundJndi.setSelection(input.isFailIfUnboundJndi());

    ProcessorType processorType = input.getProcessorType();
    if (processorType != null) {
      wProcessor.setText(processorType.getDescription());
    } else {
      wProcessor.setText(ProcessorType.PDF.getDescription());
    }

    wConnectionMappings.clearAll(false);
    if (input.getConnectionMappings() != null) {
      for (ReportConnectionMapping mapping : input.getConnectionMappings()) {
        if (mapping == null) {
          continue;
        }
        TableItem item = new TableItem(wConnectionMappings.table, SWT.NONE);
        item.setText(1, Const.NVL(mapping.getJndiName(), ""));
        item.setText(2, Const.NVL(mapping.getHopConnectionName(), ""));
      }
    }
    wConnectionMappings.removeEmptyRows();
    wConnectionMappings.setRowNums();
    wConnectionMappings.optWidth(true);

    wParameters.clearAll(false);
    if (input.getParameters() != null) {
      for (ReportParameter parameter : input.getParameters()) {
        if (parameter == null) {
          continue;
        }
        TableItem item = new TableItem(wParameters.table, SWT.NONE);
        item.setText(1, Const.NVL(parameter.getParameterName(), ""));
        item.setText(2, Const.NVL(parameter.getFieldName(), ""));
      }
    }
    wParameters.removeEmptyRows();
    wParameters.setRowNums();
    wParameters.optWidth(true);

    setFlags();
    input.setChanged(changed);
  }

  private String[] loadHopConnectionNames() {
    try {
      if (metadataProvider != null) {
        List<String> names = metadataProvider.getSerializer(DatabaseMeta.class).listObjectNames();
        if (names != null && !names.isEmpty()) {
          Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
          return names.toArray(new String[0]);
        }
      }
    } catch (Exception e) {
      // fall through — empty combo still allows free-typed connection names
    }
    return new String[0];
  }

  private void setFlags() {
    boolean fromFields = wUseValuesFromFields.getSelection();
    wInputField.setEnabled(fromFields);
    wOutputField.setEnabled(fromFields);
    wInputFile.setEnabled(!fromFields);
    wOutputFile.setEnabled(!fromFields);
  }

  private void populatePreviousFields() {
    if (gotPreviousFields) {
      return;
    }
    try {
      String nameForFields =
          !Utils.isEmpty(transformName)
              ? transformName
              : (input.getParentTransformMeta() != null
                  ? input.getParentTransformMeta().getName()
                  : null);
      IRowMeta rowMeta =
          !Utils.isEmpty(nameForFields)
              ? pipelineMeta.getPrevTransformFields(variables, nameForFields)
              : null;
      String[] fieldNames = rowMeta != null ? rowMeta.getFieldNames() : new String[0];
      wInputField.setItems(fieldNames);
      wOutputField.setItems(fieldNames);
      if (wParameters.getColumns().length > 1) {
        wParameters.getColumns()[1].setComboValues(fieldNames);
      }
      gotPreviousFields = true;
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Title"),
          BaseMessages.getString(PKG, "System.Dialog.GetFieldsFailed.Message"),
          e);
    }
  }

  private void cancel() {
    transformName = null;
    input.setChanged(changed);
    dispose();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }

    transformName = wTransformName.getText();
    input.setUseValuesFromFields(wUseValuesFromFields.getSelection());
    input.setInputFileField(wInputField.getText());
    input.setOutputFileField(wOutputField.getText());
    input.setInputFile(wInputFile.getText());
    input.setOutputFile(wOutputFile.getText());
    input.setCreateParentFolder(wCreateParentFolder.getSelection());
    input.setUseSameNameHopConnections(wUseSameNameHopConnections.getSelection());
    input.setFailIfUnboundJndi(wFailIfUnboundJndi.getSelection());

    ProcessorType type = ProcessorType.getByDescription(wProcessor.getText());
    input.setProcessorType(type != null ? type : ProcessorType.PDF);

    List<ReportConnectionMapping> connectionMappings = new ArrayList<>();
    int mappingCount = wConnectionMappings.nrNonEmpty();
    for (int i = 0; i < mappingCount; i++) {
      TableItem item = wConnectionMappings.getNonEmpty(i);
      String jndi = item.getText(1);
      String hop = item.getText(2);
      if (!Utils.isEmpty(jndi) && !Utils.isEmpty(hop)) {
        connectionMappings.add(new ReportConnectionMapping(jndi, hop));
      }
    }
    input.setConnectionMappings(connectionMappings);

    List<ReportParameter> parameters = new ArrayList<>();
    int count = wParameters.nrNonEmpty();
    for (int i = 0; i < count; i++) {
      TableItem item = wParameters.getNonEmpty(i);
      String name = item.getText(1);
      String field = item.getText(2);
      if (!Utils.isEmpty(name) && !Utils.isEmpty(field)) {
        parameters.add(new ReportParameter(name, field));
      }
    }
    input.setParameters(parameters);

    dispose();
  }
}
