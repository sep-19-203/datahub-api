package vn.teca.hddt.dataapi.application.detail;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.port.InvoiceQueryPort;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.GoodsLine;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceDetail;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;
import vn.teca.hddt.dataapi.domain.invoice.KhhdonVariants;

/** HD3 — tra cứu chi tiết một hóa đơn. Không chọn dòng đầu khi khóa trả nhiều hóa đơn (SI-18). */
@Service
public class InvoiceDetailService {
    static final int AMBIGUITY_PROBE_LIMIT = 5;

    private final InvoiceQueryPort invoices;
    private final TaxpayerStatusPort statuses;

    public InvoiceDetailService(InvoiceQueryPort invoices, TaxpayerStatusPort statuses) {
        this.invoices = invoices;
        this.statuses = statuses;
    }

    public InvoiceDetail get(String nbmst, int khmshdon, String khhdon, long shdon, LocalDate tdlap) {
        if (!statuses.configured()) {
            throw new ApiException(ErrorCode.MST_STATUS_UNAVAILABLE,
                    "Nguồn trạng thái MST người bán chưa được cấu hình (SI-24)");
        }
        List<InvoiceHeader> found = invoices.findHeaders(nbmst, khmshdon, KhhdonVariants.of(khhdon), shdon, tdlap,
                AMBIGUITY_PROBE_LIMIT);
        if (found.isEmpty()) {
            throw new ApiException(ErrorCode.INVOICE_NOT_FOUND);
        }
        if (found.size() > 1) {
            throw new ApiException(ErrorCode.AMBIGUOUS_INVOICE, "Khóa tra cứu khớp " + found.size()
                    + " hóa đơn (loại: " + found.stream().map(InvoiceHeader::loaiHd).distinct().collect(Jdk8Compat.toList()) + ")");
        }
        InvoiceHeader header = found.get(0);
        List<GoodsLine> items = invoices.findGoodsLines(header);
        String status = statuses.findStatus(header.nbmst()).orElse(null);
        return new InvoiceDetail(header, status, items);
    }
}
