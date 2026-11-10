# Kiến trúc hddt-data-api-service

## 1. Lớp và phụ thuộc (Clean Architecture, phụ thuộc hướng vào trong)

| Lớp | Gói | Phụ thuộc được phép |
|---|---|---|
| Domain | `domain.*` (MonthRange, InvoiceRecord, CsvLayouts, SalesMappingPolicy, ReconciliationEngine, Job…) | JDK thuần |
| Application | `application.*` (service, handler, port, CSV codec, S3UrlPolicy, ErrorCode) | domain, Spring stereotype, Jackson, Commons CSV |
| Adapter vào | `adapter.web.*` (controller, DTO, filter bảo mật, exception handler) | application |
| Adapter ra | `infrastructure.*` (Doris JDBC, DSE driver 4.15, S3 SDK v2, worker, health) | application ports |

## 2. Luồng xử lý

**Sync (HD3, HD7)**: filter (X-Request-Id, client secret, quyền route, rate limit, giới hạn body) → controller validate →
service → port Doris (JdbcTemplate timeout 30 s) → DTO → envelope 200.

**Async (HD1, HD5, HD8, TMS)**: controller validate + gate nghiệp vụ (MST status / rules-confirmed / s3url whitelist) →
`JobSubmissionService` (băm payload chuẩn tắc; `(client, X-Request-Id)` UNIQUE; giới hạn job hoạt động theo client/MST) →
`api_job` QUEUED (DSE, `api_job_by_request … IF NOT EXISTS`) → 202/200 `TIEP_NHAN`. Worker: `claimNext` (quét `api_job_active`
+ LWT `UPDATE … IF state=? AND attempt_no=?`) → RUNNING + lease (đồng hồ ứng dụng) → heartbeat lease/3 → handler → kiểm tra kết quả → `markSucceeded` có rào
(worker_id + attempt_no + state) → `HOAN_THANH`. Lỗi tạm thời ⇒ RETRY_WAIT (backoff mũ, công khai vẫn `DANG_XU_LY`);
lỗi vĩnh viễn / hết lượt ⇒ `LOI` với `mta` an toàn. Pod chết ⇒ lease hết hạn ⇒ pod khác nhận lại (attempt+1); kết quả
của pod cũ bị rào loại bỏ.

## 3. Kết xuất HD1/HD5

1. Xóa object còn sót dưới `export/query-result/<jobId>/` (lần thử trước chưa công bố).
2. (HD1) truy vấn rẻ MIN/MAX(TDLAP) trên `HD_MST_LIST` để cắt partition `HOADON_HDR`.
3. `SELECT CONCAT_WS(',', <14|16 phần tử RFC 4180>) FROM HD_MST_LIST l JOIN HOADON_HDR h … LEFT JOIN (trạng thái MST) s …
   INTO OUTFILE "s3://<bucket>/export/query-result/<jobId>/" FORMAT AS CSV PROPERTIES(…)`
   — Doris đặt tên `<queryId>_<n>.csv`, trả `FileNumber/TotalRows/FileSize/URL` ⇒ `url = s3://…/<queryId>_*` (đúng dạng ví dụ đặc tả).
4. Kiểm tra: số file & tổng byte trên S3 = báo cáo Doris; kết quả ≤ 256 MB ⇒ đọc lại đếm bản ghi + số cột.
5. Manifest (file, size, ETag, rowCount) lưu `api_job.result_manifest`; mới công bố HOAN_THANH.
Dự phòng `JDBC_STREAM`: đọc luồng cột `csv_line` (fetchSize MIN_VALUE) → multipart 8 MB → cùng quy ước tên.

## 4. Bằng chứng tối ưu truy vấn (Doris DEV 4.0.8, EXPLAIN)

| Truy vấn | Cắt tỉa |
|---|---|
| HD1/HD5 xuất | `HD_MST_LIST`: partitions 1/2, **tablets 1/48** (bucket theo MST); `HOADON_HDR`: partition theo TDLAP (HD5 trực tiếp, HD1 qua MIN/MAX) — tablets 48/48 ở plan, runtime filter theo ID khi chạy |
| HD3 | LIST (KY + MST ⇒ 1 bucket) ⨝ HDR (TDLAP = ? ⇒ 1 partition + prefix key) |
| HD7 mua vào | `HD_MST_SUMMARY` AGGREGATE, KY + MST |
| HD7 bán ra | LIST BAN_RA ⨝ HDR + `h.TDLAP` khoảng tháng; tách THTTLTSUAT; HĐ thiếu THTTLTSUAT ⨝ DTL (hint `leading(d {h l})`, DTL nhận runtime filter IDHDON) |
| HD8 / TMS | 1 truy vấn / lô (mặc định 500 dòng), IN NBMST + SHDON, lọc khóa chính xác ở Java |

Khi tải lớn: cân nhắc bloom filter/inverted index `HOADON_HDR.NBMST`, bảng summary theo thuế suất (TD-API-07).

## 5. Bảo mật

APIC xác thực trước; backend **tự kiểm** lại client secret (SHA-256 trong Secret, so constant-time) + quyền theo route,
chỉ client TMS gọi được API TMS; giới hạn MST theo client (`all-mst`/`allowed-msts`); job chỉ chủ sở hữu xem được;
`s3url` chỉ `s3://` + bucket/prefix whitelist (chống SSRF/path traversal); SQL chỉ tham số hóa; body ≤ 64 KB;
rate limit theo client; tùy chọn token gateway APIC; Route TLS + IP whitelist + NetworkPolicy; log AUDIT không chứa secret
và che MST; thông báo lỗi Doris được che `s3.secret_key/access_key`.

## 6. Dữ liệu bền vững

DSE keyspace `datahub_hddt` (`cql/V1__job_store.cql`, TTL metadata 120 ngày): `api_job`, `api_job_by_request`, `api_job_active`,
`api_job_event`, `api_job_finished`, `refund_batch`, `refund_item` (theo lần thử + bucket), `refund_applied_content` (LWT).
Doris chỉ đọc (+ OUTFILE) + bảng tạm `doris.NNT_HIS`, `doris.NNT_HKD` (theo `Doris_map DKT.xlsx`) + **sổ hoàn `datahub_hddt.HD_SO_HOAN`** (ghi duy nhất của dịch vụ vào Doris, `INSERT … WITH LABEL`). Doris bị loại làm kho job: không insert-if-absent, không CAS (TD-API-03b).

## Sổ hoàn TMS (01/11, TD-API-27/28)

```
CSV TMS (S3) ─► lượt 1: parse + SHA-256 + trùng khóa trong file
              ─► mỗi lô: Doris LIST⨝HDR (BAN_RA, theo tháng) → NOT_FOUND / AMBIGUOUS
                          Doris HD_SO_HOAN (DA_HOAN, MCV khác)  → ALREADY_REFUNDED
              ─► DSE refund_item (staging: trạng thái từng dòng + dữ liệu sẽ ghi)
   một dòng lỗi ⇒ job LOI, không ghi gì   │   tất cả hợp lệ ⇓
              ─► LWT refund_applied_content (cùng SHA đã ghi ở job khác ⇒ HDDT-409-DUPLICATE)
              ─► khối 1000 dòng: INSERT INTO HD_SO_HOAN WITH LABEL sohoan_<jobId>_<khối> (thử lại không nhân bản)
              ─► COUNT(DA_HOAN, MCV = jobId) == số dòng hợp lệ ⇒ APPLIED, HOAN_THANH
```
`HD_SO_HOAN`: UNIQUE KEY (TDLAP, NBMST, KHMSHDON, KHHDON, SHDON, MCV) — một HĐ có thể nhiều lần ghi (nhiều MCV), TTHOAN
DA_HOAN/DA_HUY, không xóa. MCV = jobId; MSTHSO suy từ NMMST của hóa đơn (`NGUON_MSTHSO='HD_NMMST'`); MHSO/SQDINH/NQDINH/TDCNHOAN
chờ TMS bổ sung hợp đồng. REFUND_UPDATE chạy tối đa 1 job một lúc (giới hạn mềm, KI-API-21).
