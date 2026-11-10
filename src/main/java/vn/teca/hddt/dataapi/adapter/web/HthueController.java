package vn.teca.hddt.dataapi.adapter.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.DeclarationSuggestionData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.DeclarationSuggestionRequest;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.InvoiceDetailData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.JobAckData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.JobStatusData;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.PurchaseListRequest;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.PurchaseVerificationRequest;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.RefundUpdateRequest;
import vn.teca.hddt.dataapi.adapter.web.dto.ApiDtos.SalesListRequest;
import vn.teca.hddt.dataapi.adapter.web.dto.DtoMapper;
import vn.teca.hddt.dataapi.application.detail.InvoiceDetailService;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.export.RealtimeResultWaiter;
import vn.teca.hddt.dataapi.application.job.JobPayloads;
import vn.teca.hddt.dataapi.application.job.JobQueryService;
import vn.teca.hddt.dataapi.application.job.JobSubmissionService;
import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;
import vn.teca.hddt.dataapi.application.storage.S3UrlPolicy;
import vn.teca.hddt.dataapi.application.suggest.DeclarationSuggestionService;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.common.TaxCode;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceDetail;
import vn.teca.hddt.dataapi.domain.job.Job;
import vn.teca.hddt.dataapi.domain.job.JobType;

/**
 * 7 API của sheet `API HDDT - TTHC - TMS.xlsx`. Controller chỉ kiểm tra hợp đồng + quyền, không chứa SQL/nghiệp vụ.
 * Sync ⇒ HTTP 200; Async ⇒ HTTP 202 (SI-02); TMS theo cấu hình (SI-03).
 */
@RestController
@RequestMapping("/hthue/v1")
@Validated
@Tag(name = "hthue", description = "API dữ liệu HĐĐT phục vụ hoàn thuế (TTHC/TMS qua APIC)")
public class HthueController {
    private final JobSubmissionService submissions;
    private final JobQueryService jobQueries;
    private final InvoiceDetailService detailService;
    private final DeclarationSuggestionService suggestionService;
    private final TaxpayerStatusPort statuses;
    private final S3UrlPolicy urlPolicy;
    private final RealtimeResultWaiter realtime;
    private final HddtProperties props;

    public HthueController(JobSubmissionService submissions, JobQueryService jobQueries,
                           InvoiceDetailService detailService, DeclarationSuggestionService suggestionService,
                           TaxpayerStatusPort statuses, S3UrlPolicy urlPolicy, RealtimeResultWaiter realtime,
                           HddtProperties props) {
        this.submissions = submissions;
        this.jobQueries = jobQueries;
        this.detailService = detailService;
        this.suggestionService = suggestionService;
        this.statuses = statuses;
        this.urlPolicy = urlPolicy;
        this.realtime = realtime;
        this.props = props;
    }

    @Operation(summary = "HD1 — Danh sách hóa đơn mua vào (Async, CSV 15 cột không header — API v3)")
    @PostMapping(path = "/mua-vao", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<JobStatusData>> purchaseList(@Valid @RequestBody PurchaseListRequest body,
                                                                   HttpServletRequest req) {
        return listExport(req, Direction.MUA_VAO, JobType.PURCHASE_LIST, body.nmmst(), body.tthang(), body.dthang(),
                body.lbttmsthue(), body.lbbhphap());
    }

    @Operation(summary = "HD5 — Danh sách hóa đơn bán ra (Async, < 10k dòng trả ngay; CSV 18 cột không header)")
    @PostMapping(path = "/ban-ra", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<JobStatusData>> salesList(@Valid @RequestBody SalesListRequest body,
                                                                HttpServletRequest req) {
        return listExport(req, Direction.BAN_RA, JobType.SALES_LIST, body.nbmst(), body.tthang(), body.dthang(),
                body.lbttmsthue(), body.lbbhphap());
    }

    private ResponseEntity<ApiResponse<JobStatusData>> listExport(HttpServletRequest req, Direction direction,
                                                                  JobType type, String mst, String tthang, String dthang,
                                                                  boolean lbttmsthue, boolean lbbhphap) {
        ClientPrincipal client = RequestContext.principal(req);
        client.requireMst(mst);
        MonthRange.parse(tthang, dthang, props.api().maxMonths());
        requireStatusSource();
        JobPayloads.ListExportParams params =
                new JobPayloads.ListExportParams(direction.name(), mst, tthang, dthang, lbttmsthue, lbbhphap);
        Job job = submissions.submit(type, client, requestId(req), params, mst);
        job = realtime.awaitIfSmall(job, params); // SI-06 (HD1, HD5): < 10k dòng ⇒ trả url/tsdong ngay
        return ResponseEntity.accepted().body(ApiResponse.ok(RequestContext.traceId(req), 202, DtoMapper.status(job)));
    }

    @Operation(summary = "HD3 — Chi tiết một hóa đơn (Sync)")
    @GetMapping("/chi-tiet")
    public ResponseEntity<ApiResponse<InvoiceDetailData>> detail(@RequestParam String nbmst, @RequestParam String khmshdon,
                                                                 @RequestParam String khhdon, @RequestParam String shdon,
                                                                 @RequestParam String tdlap, HttpServletRequest req) {
        ClientPrincipal client = RequestContext.principal(req);
        TaxCode.require("nbmst", nbmst);
        client.requireMst(nbmst);
        if (!khmshdon.matches("\\d")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "khmshdon phải là 1 chữ số");
        }
        if (Jdk8Compat.isBlank(khhdon) || khhdon.getBytes(StandardCharsets.UTF_8).length > 11) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "khhdon rỗng hoặc vượt 11 byte");
        }
        if (!shdon.matches("\\d{1,10}") || Long.parseLong(shdon) > Integer.MAX_VALUE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "shdon phải là số nguyên không âm");
        }
        LocalDate d = DateFormats.parseDate("tdlap", tdlap);
        InvoiceDetail detail = detailService.get(nbmst, Integer.parseInt(khmshdon), khhdon, Long.parseLong(shdon), d);
        return ResponseEntity.ok(ApiResponse.ok(RequestContext.traceId(req), 200, DtoMapper.detail(detail)));
    }

    @Operation(summary = "HD7 — Thông tin tổng hợp gợi ý tờ khai (Sync)")
    @PostMapping(path = "/goi-y", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DeclarationSuggestionData>> suggest(@Valid @RequestBody DeclarationSuggestionRequest body,
                                                                          HttpServletRequest req) {
        ClientPrincipal client = RequestContext.principal(req);
        client.requireMst(body.mst());
        MonthRange range = MonthRange.parse(body.tthang(), body.dthang(), props.hd7().maxMonths());
        DeclarationSuggestionService.Suggestion s = suggestionService.suggest(body.mst(), range);
        return ResponseEntity.ok(ApiResponse.ok(RequestContext.traceId(req), 200, DtoMapper.suggestion(s)));
    }

    @Operation(summary = "HD8 — Kiểm tra chi tiết danh sách hóa đơn mua vào từ file S3 (Async)")
    @PostMapping(path = "/mua-vao/kiem-tra", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<JobAckData>> verify(@Valid @RequestBody PurchaseVerificationRequest body,
                                                          HttpServletRequest req) {
        ClientPrincipal client = RequestContext.principal(req);
        client.requireMst(body.nmmst());
        if (!props.hd8().rulesConfirmed()) {
            throw new ApiException(ErrorCode.RULE_NOT_CONFIRMED,
                    "Quy tắc đối soát HD8 (schema file vào, trường so sánh, sai số) chưa được phê duyệt (SI-12, SI-13)");
        }
        LocalDate tngay = DateFormats.parseDate("tngay", body.tngay());
        LocalDate dngay = DateFormats.parseDate("dngay", body.dngay());
        if (tngay.isAfter(dngay)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "tngay phải nhỏ hơn hoặc bằng dngay");
        }
        if (ChronoUnit.DAYS.between(tngay, dngay) + 1 > props.hd8().maxDays()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Khoảng ngày vượt " + props.hd8().maxDays() + " ngày");
        }
        urlPolicy.parse(body.s3url(), props.s3().hd8InputPrefixes());
        requireStatusSource();
        Job job = submissions.submit(JobType.PURCHASE_VERIFY, client, requestId(req),
                new JobPayloads.VerifyParams(body.nmmst(), body.tngay(), body.dngay(), body.s3url()), body.nmmst());
        return ResponseEntity.accepted().body(ApiResponse.ok(RequestContext.traceId(req), 202, DtoMapper.ack(job)));
    }

    @Operation(summary = "TMS — Cập nhật danh sách hóa đơn đã hoàn thuế (xử lý dạng job)")
    @PostMapping(path = "/cap-nhat-hoa-don-hoan-thue", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<JobAckData>> refundUpdate(@Valid @RequestBody RefundUpdateRequest body,
                                                                HttpServletRequest req) {
        ClientPrincipal client = RequestContext.principal(req);
        urlPolicy.parse(body.s3url(), props.s3().tmsInputPrefixes());
        Job job = submissions.submit(JobType.REFUND_UPDATE, client, requestId(req),
                new JobPayloads.RefundParams(body.s3url()), null);
        int status = props.api().tmsAcceptedHttpStatus();
        return ResponseEntity.status(status).body(ApiResponse.ok(RequestContext.traceId(req), status, DtoMapper.ack(job)));
    }

    @Operation(summary = "Kiểm tra và lấy kết quả job (Sync)")
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ApiResponse<JobStatusData>> job(@PathVariable String jobId, HttpServletRequest req) {
        Job job = jobQueries.get(jobId, RequestContext.principal(req));
        return ResponseEntity.ok(ApiResponse.ok(RequestContext.traceId(req), 200, DtoMapper.status(job)));
    }

    private void requireStatusSource() {
        if (!statuses.configured()) {
            throw new ApiException(ErrorCode.MST_STATUS_UNAVAILABLE,
                    "Nguồn trạng thái MST chưa được cấu hình; không thể lọc/điền ttmst (SI-24)");
        }
    }

    /**
     * X-Request-Id đã được filter kiểm (UUID) và đặt làm traceId; request ESB không gửi X-Request-Id thì traceId là UUID
     * tự sinh (TD-API-40) ⇒ mỗi lần gọi là một khóa idempotency mới.
     */
    private static String requestId(HttpServletRequest req) {
        String traceId = RequestContext.traceId(req);
        return traceId != null ? traceId : req.getHeader(RequestContext.HEADER_REQUEST_ID);
    }
}
