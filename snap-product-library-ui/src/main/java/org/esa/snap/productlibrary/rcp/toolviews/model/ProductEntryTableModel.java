/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.productlibrary.rcp.toolviews.model;

import org.esa.snap.productlibrary.db.ProductEntry;
import org.esa.snap.productlibrary.rcp.toolviews.model.dataprovider.DataProvider;
import org.esa.snap.productlibrary.rcp.toolviews.model.dataprovider.IDProvider;
import org.esa.snap.productlibrary.rcp.toolviews.model.dataprovider.PropertiesProvider;
import org.esa.snap.productlibrary.rcp.toolviews.model.dataprovider.QuicklookProvider;
import org.esa.snap.rcp.util.Dialogs;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;
import java.util.List;

public class ProductEntryTableModel extends AbstractTableModel {

    private final ProductEntry[] productEntryList;
    final List<DataProvider> dataProviders = new ArrayList<>(5);
    private final List<TableColumn> columnList = new ArrayList<>();

    public ProductEntryTableModel(final ProductEntry[] productList, boolean minimalView) {
        this.productEntryList = productList;
        dataProviders.add(new IDProvider());
        dataProviders.add(new PropertiesProvider(minimalView));
        if (!minimalView) {
            try {
                dataProviders.add(new QuicklookProvider());
            } catch (Exception e) {
                e.printStackTrace();
                Dialogs.showError(e.getMessage());
            }
        }
        for (final DataProvider provider : dataProviders) {
            final TableColumn tableColumn = provider.getTableColumn();
            tableColumn.setModelIndex(getColumnCount());
            columnList.add(tableColumn);
        }
    }

    public DataProvider getDataProvider(final int columnIndex) {
        if (columnIndex >= 0 && columnIndex < dataProviders.size()) {
            return dataProviders.get(columnIndex);
        }
        return null;
    }

    public TableColumnModel getColumnModel() {
        final TableColumnModel columnModel = new DefaultTableColumnModel();
        for (TableColumn aColumnList : columnList) {
            columnModel.addColumn(aColumnList);
        }
        return columnModel;
    }

    public int getRowCount() {
        return productEntryList != null ? productEntryList.length : 0;
    }

    public int getColumnCount() {
        return columnList.size();
    }

 /*   @Override
    public Class getColumnClass(final int columnIndex) {
        if (repository != null) {
            if (repository.getEntryCount() > 0) {
                final Object data = repository.getEntry(0).getData(columnIndex);
                if (data != null) {
                    return data.getClass();
                }
            }
        }
        return Object.class;
    }   */

    public Object getValueAt(final int rowIndex, final int columnIndex) {
        if (productEntryList != null) {
            final ProductEntry entry = productEntryList[rowIndex];
            if (entry != null)
                return entry;
        }
        return null;
    }

    @Override
    public String getColumnName(final int columnIndex) {
        if (columnIndex >= 0 && columnIndex < columnList.size()) {
            final TableColumn column = columnList.get(columnIndex);
            return column.getHeaderValue().toString();
        }
        return "";
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
        if (columnIndex >= columnList.size()) {
            return false;
        }
        final TableColumn column = columnList.get(columnIndex);
        return column.getCellEditor() != null;
    }

}
