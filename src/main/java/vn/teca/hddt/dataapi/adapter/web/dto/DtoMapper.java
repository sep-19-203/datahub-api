package vn.teca.hddt.dataapi.adapter.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.DeclarationSuggestionData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.GoodsDto;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.InvoiceDetailData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.JobAckData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.JobStatusData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.PurchaseSummaryDto;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.SalesSummaryDto;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.TaxRateDto;
import vn.teca.hddt.dataapi.application.suggest.DeclarationSuggestionService.Suggestion;
import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.hd7.SalesField;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceDetail;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;
import vn.teca.hddt.dataapi.domain.job.Job;

/** Chuyển mô hình nghiệp vụ sang DTO hợp đồng. */
public final class DtoMapper {
    private DtoMapper() {
    }

    /** url/tsdong chỉ khi kết quả đã công bố (HOAN_THANH, chưa hết hạn). tdkxuat = thời điểm nhận yêu cầu. */
    public static JobStatusData status(Job j) {
        boolean published = j.resultPublished();
        String mta = j.description();
        if (j.expiredAt() != null) {
            mta = "Kết quả đã hết hạn lưu trữ";
        }
        return new JobStatusData(j.jobId().toString(), j.jobCode(), j.publicStatus().name(), mta,
                DateFormats.format(LocalDateTime.ofInstant(j.createdAt(), DateFormats.VN)),
                published ? j.resultUrl() : null, published ? j.rowCount() : null);
    }

    public static JobAckData ack(Job j) {
        return new JobAckData(j.jobId().toString(), j.jobCode(), j.publicStatus().name());
    }

    public static InvoiceDetailData detail(InvoiceDetail d) {
        InvoiceHeader h = d.header();
        return new InvoiceDetailData(h.nbmst(), h.khmshdon(), h.khhdon(), h.shdon(), DateFormats.format(h.tdlap()),
                DateFormats.format(h.nky()), h.tthai(), h.nbten(), h.tgtcthue(), h.tgtthue(),
                h.thttltsuat().stream().map(t -> new TaxRateDto(t.tsuat(), t.thtien(), t.tthue())).collect(Jdk8Compat.toList()),
                d.ttmstnban(), h.bhphap(), h.bhpldo(), DateFormats.format(h.bhpngay()),
                d.items().stream().map(g -> new GoodsDto(g.ten(), g.thtcthue(), g.ltsuat(), g.tthue())).collect(Jdk8Compat.toList()),
                h.lddctthe());
    }

    public static DeclarationSuggestionData suggestion(Suggestion s) {
        Map<SalesField, BigDecimal> b = s.sales();
        return new DeclarationSuggestionData(
                new PurchaseSummaryDto(s.purchases().pretax(), s.purchases().tax(), s.purchases().invoiceCount()),
                new SalesSummaryDto(b.get(SalesField.TGTBR0), b.get(SalesField.TGTBR5), b.get(SalesField.TGTTBR5),
                        b.get(SalesField.TGTBR10), b.get(SalesField.TGTTBR10), b.get(SalesField.TGTBRKCT),
                        b.get(SalesField.TGTBRKKKNT), b.get(SalesField.TGTBRKTGTT), b.get(SalesField.TGTBRKTPVDC)));
    }
}
