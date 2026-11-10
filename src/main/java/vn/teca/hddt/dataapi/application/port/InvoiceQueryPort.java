package vn.teca.hddt.dataapi.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import vn.teca.hddt.dataapi.domain.invoice.GoodsLine;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;

/** Đọc hóa đơn chi tiết (HD3). */
public interface InvoiceQueryPort {
    /** Mọi header khớp khóa tra cứu (không cắt dòng đầu); giới hạn {@code limit} để phát hiện mơ hồ. */
    List<InvoiceHeader> findHeaders(String nbmst, int khmshdon, Set<String> khhdonVariants, long shdon, LocalDate tdlap,
                                    int limit);

    /** Toàn bộ dòng hàng hóa của một hóa đơn trong một truy vấn (không N+1), theo STT. */
    List<GoodsLine> findGoodsLines(InvoiceHeader header);
}
