package guru.interlis.gui;

import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.gui.presentation.DiagnosticPresenter;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class ProblemsTableModel extends AbstractTableModel {
    private static final List<String> COLUMNS = List.of("Severity", "Code", "Message", "Source", "Position");
    private List<DiagnosticPresenter.Row> rows = List.of();

    void show(List<DiagnosticPresenter.Row> visible) {
        rows = List.copyOf(visible);
        fireTableDataChanged();
    }

    DiagnosticId idAt(int row) {
        return rows.get(row).id();
    }

    String sourceDetailAt(int row) {
        return rows.get(row).sourceDetail();
    }

    int rowOf(DiagnosticId id) {
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row).id().equals(id)) {
                return row;
            }
        }
        return -1;
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.size();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.get(column);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.severity();
            case 1 -> row.code();
            case 2 -> row.message();
            case 3 -> row.source();
            case 4 -> row.position().orElse(null);
            default -> throw new IndexOutOfBoundsException(columnIndex);
        };
    }
}
