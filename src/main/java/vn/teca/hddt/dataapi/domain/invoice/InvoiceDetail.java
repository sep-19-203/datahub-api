package vn.teca.hddt.dataapi.domain.invoice;

import java.util.List;
import java.util.Objects;

/** Kết quả HD3: header + trạng thái MST người bán + danh sách hàng hóa. */
public final class InvoiceDetail {
    private final InvoiceHeader header;
    private final String ttmstnban;
    private final List<GoodsLine> items;

    public InvoiceDetail(InvoiceHeader header, String ttmstnban, List<GoodsLine> items) {
        this.header = header;
        this.ttmstnban = ttmstnban;
        this.items = items;
    }

    public InvoiceHeader header() {
        return header;
    }

    public String ttmstnban() {
        return ttmstnban;
    }

    public List<GoodsLine> items() {
        return items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceDetail)) {
            return false;
        }
        InvoiceDetail other = (InvoiceDetail) o;
        return Objects.equals(header, other.header)
                && Objects.equals(ttmstnban, other.ttmstnban)
                && Objects.equals(items, other.items);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(header);
        result = 31 * result + Objects.hashCode(ttmstnban);
        result = 31 * result + Objects.hashCode(items);
        return result;
    }

    @Override
    public String toString() {
        return "InvoiceDetail[header=" + header
                + ", ttmstnban=" + ttmstnban
                + ", items=" + items + "]";
    }
}
