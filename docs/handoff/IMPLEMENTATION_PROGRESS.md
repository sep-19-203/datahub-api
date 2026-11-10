# Tiến độ triển khai

## Commit
| Commit | Nội dung |
|---|---|
| `522143a` | Khởi tạo module, 7 API, job engine, 85 test |
| `cda00d3` | IT PostgreSQL/MinIO/Doris DEV; cắt partition HDR cho HD1 |
| `4e23f6b` | OpenAPI + test, OpenShift, tài liệu handoff, log WARN cho từ chối nghiệp vụ |
| `416aea8` (vòng 2) | Kho job DSE (LWT) thay PostgreSQL; NNT_HIS tạm; HD8 bật theo chốt; OUTFILE thật; chạy local java -jar; sửa lỗi DSE timestamp & MinIO Content-MD5 |
| `ea7ff59` (vòng 3) | HD8 theo quy tắc chốt 01/11 (so tgtcthue, tgtthue; trả nguyên dòng vào) |
| `b605886` (vòng 4, 01/11) | Sổ hoàn TMS: Doris `HD_SO_HOAN` + staging DSE (V2) + chống hoàn trùng/gửi trùng + đối chiếu; đánh giá Tsuat cho HD7 |
| `db234ae` (vòng 5, 01/11) | HD7 theo chốt: tách THTTLTSUAT + dòng hàng DTL, 8%→32/33, 32b/34a null, KHAC bỏ qua |
| (01/11) | Tách thành repo riêng `hddt1/datahub_hddt/hddt-data-api-service` (`git subtree split`, giữ lịch sử; mã commit đổi so với `hddt-toolkit`) |

## Thành phần đã có
- Domain: `MonthRange`, `TaxCode`, `DateFormats`, `KhhdonVariants`, `InvoiceRecord`, `CsvLayouts`, `SalesMappingPolicy`,
  `ComparisonPolicy`/`ReconciliationEngine`, `Job`/`JobState`/`JobType`.
- Application: `JobSubmissionService`, `JobQueryService`, `InvoiceDetailService`, `DeclarationSuggestionService`,
  `ListExportHandler`, `PurchaseVerificationHandler`, `RefundUpdateHandler`, CSV codec, `S3UrlPolicy`, `JobFailures`.
- Infrastructure: Doris adapters (HD3, HD7, tra lô, trạng thái MST, xuất OUTFILE/JDBC_STREAM), `DseJobRepository`,
  `DseRefundStore` (+ `DseConfig`, `cql/V1__job_store.cql`, `cql/V2__refund_ledger_staging.cql`), `DorisRefundLedgerAdapter` (`sql/doris/02_hd_so_hoan.sql`), `S3ObjectStorageAdapter`, `JobWorker`, `ResultRetentionJanitor`, health.
- Web: `HthueController`, `ApiExceptionHandler`, `ClientAuthenticationFilter`, `ClientRegistry`, `RateLimiter`.
- `cql/V1__job_store.cql`; `sql/doris/01_nnt_his_tam.sql`; `application.yml` (+local/dev/prod); `scripts/run-local.sh`; Dockerfile; OpenShift yaml (chưa dùng).

## SQL đã viết (đều tham số hóa, kiểm chứng chạy trên Doris DEV 4.0.8)
HD3 header (LIST⨝HDR) + DTL; HD7 summary mua vào, bán ra theo mã tsuat (THTTLTSUAT + DTL, EXPLAIN DEV); HD8 tra lô mua vào; TMS tra lô bán ra theo tháng;
trạng thái MST (bọc nguồn cấu hình); xuất HD1/HD5 (+ MIN/MAX TDLAP, + OUTFILE); EXPLAIN đã lưu ở TEST_RESULTS.
