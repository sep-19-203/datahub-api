package vn.teca.hddt.dataapi.domain.csv;

import java.util.Objects;

/**
 * Một cột CSV: thứ tự 1-based, tên đúng đặc tả, kiểu, bắt buộc, ô nguồn trong workbook.
 * {@code struck} = dòng đặc tả bị gạch ngang: vẫn khai báo (tên, kiểu, ô nguồn) để bật lại khi khách hàng yêu cầu, nhưng
 * KHÔNG phải cột của file (không nằm trong {@link CsvLayout#columns()}, {@code index} = 0).
 */
public final class CsvColumn {
    private final int index;
    private final String name;
    private final ValueKind kind;
    private final boolean required;
    private final String specCell;
    private final boolean struck;

    public CsvColumn(int index, String name, ValueKind kind, boolean required, String specCell) {
        this(index, name, kind, required, specCell, false);
    }

    public CsvColumn(int index, String name, ValueKind kind, boolean required, String specCell, boolean struck) {
        this.index = index;
        this.name = name;
        this.kind = kind;
        this.required = required;
        this.specCell = specCell;
        this.struck = struck;
    }

    public int index() {
        return index;
    }

    public String name() {
        return name;
    }

    public ValueKind kind() {
        return kind;
    }

    public boolean required() {
        return required;
    }

    public String specCell() {
        return specCell;
    }

    public boolean struck() {
        return struck;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CsvColumn)) {
            return false;
        }
        CsvColumn other = (CsvColumn) o;
        return index == other.index
                && Objects.equals(name, other.name)
                && Objects.equals(kind, other.kind)
                && required == other.required
                && Objects.equals(specCell, other.specCell)
                && struck == other.struck;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(index);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(kind);
        result = 31 * result + Boolean.hashCode(required);
        result = 31 * result + Objects.hashCode(specCell);
        result = 31 * result + Boolean.hashCode(struck);
        return result;
    }

    @Override
    public String toString() {
        return "CsvColumn[index=" + index
                + ", name=" + name
                + ", kind=" + kind
                + ", required=" + required
                + ", specCell=" + specCell
                + ", struck=" + struck + "]";
    }
}
