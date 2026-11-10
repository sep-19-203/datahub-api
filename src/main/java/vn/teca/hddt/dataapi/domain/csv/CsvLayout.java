package vn.teca.hddt.dataapi.domain.csv;

import java.util.List;
import java.util.Objects;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/**
 * Bố cục file CSV (không header). {@link #columns()} chỉ gồm cột thật của file; cột khai báo nhưng gạch ngang trong đặc tả
 * ({@link CsvColumn#struck()}) chỉ có trong {@link #declared()} — giữ lại để bật lại sau, không bao giờ ghi/đọc.
 */
public final class CsvLayout {
    private final String name;
    private final List<CsvColumn> declared;
    private final List<CsvColumn> columns;

    public CsvLayout(String name, List<CsvColumn> declared) {
        this.name = name;
        this.declared = Jdk8Compat.listCopyOf(declared);
        this.columns = this.declared.stream().filter(c -> !c.struck()).collect(Jdk8Compat.toList());
    }

    public String name() {
        return name;
    }

    public List<CsvColumn> columns() {
        return columns;
    }

    /** Mọi cột đã khai báo theo thứ tự đặc tả, kể cả cột gạch ngang (không xuất). */
    public List<CsvColumn> declared() {
        return declared;
    }

    public int size() {
        return columns.size();
    }

    public CsvColumn column(String fieldName) {
        return columns.stream().filter(c -> c.name().equals(fieldName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không có cột " + fieldName + " trong " + name));
    }

    public List<String> names() {
        return columns.stream().map(CsvColumn::name).collect(Jdk8Compat.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CsvLayout)) {
            return false;
        }
        CsvLayout other = (CsvLayout) o;
        return Objects.equals(name, other.name)
                && Objects.equals(declared, other.declared);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(declared);
        return result;
    }

    @Override
    public String toString() {
        return "CsvLayout[name=" + name
                + ", declared=" + declared + "]";
    }
}
