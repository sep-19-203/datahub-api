# hddt-data-api-service

Dịch vụ REST cung cấp dữ liệu hóa đơn điện tử từ Apache Doris 4.0.8 (`datahub_hddt`) cho TTHC/TMS qua API Connect (APIC):
7 API của `API HDDT - TTHC - TMS.xlsx`.

| API | Method / Path | Loại | Trạng thái (chi tiết: `docs/handoff/API_COVERAGE.md`) |
|---|---|---|---|
| HD1 | `POST /hthue/v1/mua-vao` | Async → CSV 15 cột (API v3) | Chạy thật DEV (OUTFILE → MinIO); trạng thái MST từ `NNT_HIS` tạm |
| HD3 | `GET /hthue/v1/chi-tiet` | Sync | Chạy thật DEV; trạng thái MST từ `NNT_HIS` tạm |
| HD5 | `POST /hthue/v1/ban-ra` | Async → CSV 16 cột | Chạy thật DEV (OUTFILE → MinIO) |
| HD7 | `POST /hthue/v1/goi-y` | Sync | **BLOCKED** (SI-09/10/11/41; đánh giá `docs/spec/HD7_DANH_GIA_TSUAT.md`): trả 501 thay vì số 0 |
| HD8 | `POST /hthue/v1/mua-vao/kiem-tra` | Async → CSV 14 cột (chỉ dòng không hợp lệ) | Chạy thật DEV; file vào = CSV HD1; so tgtcthue, tgtthue (khớp tuyệt đối); trả nguyên dòng vào |
| TMS | `POST /hthue/v1/cap-nhat-hoa-don-hoan-thue` | Job | Kiểm tra từng dòng → ghi sổ hoàn Doris `HD_SO_HOAN` (chống hoàn trùng); chờ kiểm chứng trên Doris DEV |
| Job | `GET /hthue/v1/job/{jobId}` | Sync | Xong |

## Kiến trúc tóm tắt

```
APIC ──TLS──► Route ─► [api pods] ClientAuthenticationFilter ─► HthueController
                         │  HD3/HD7: Doris (JdbcTemplate, timeout 30 s)
                         │  HD1/HD5/HD8/TMS: JobSubmissionService ─► DSE datahub_hddt.api_job (LWT idempotency)
               [worker] JobWorker ─ LWT claim + lease/heartbeat ─► handler
                         ├─ ListExportHandler: Doris SELECT … INTO OUTFILE s3:// (CSV dựng sẵn trong SQL) → kiểm tra → manifest
                         ├─ PurchaseVerificationHandler: đọc luồng CSV S3 → đối soát theo lô với Doris → CSV lệch
                         └─ RefundUpdateHandler: đọc luồng CSV S3 → kiểm tra từng dòng → refund_batch/refund_item (staging) → Doris HD_SO_HOAN (INSERT WITH LABEL)
```
Chi tiết: `docs/architecture/ARCHITECTURE.md`, triển khai: `docs/architecture/DEPLOYMENT_OPENSHIFT.md`, hợp đồng: `docs/api/openapi.yaml`,
phân tích đặc tả: `docs/spec/`.

## Build & test

```bash
export JAVA_HOME=<JDK 8>                              # Java 8 + Spring Boot 2.7.18 (migration 02/11, xem ../migration-reports/)
MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
"$MVN" -B test                                        # unit + contract
IT_DSE_CONTACT_POINTS=<host:9042,...> IT_DSE_LOCAL_DC=DC1 IT_DSE_USERNAME=<u> IT_DSE_PASSWORD=<p> \
IT_S3_ENDPOINT=http://<minio>:9000 IT_S3_ACCESS_KEY=<k> IT_S3_SECRET_KEY=<s> IT_DORIS_OUTFILE_S3_ENDPOINT=http://<minio>:9000 \
IT_DORIS_URL='jdbc:mysql://<doris-fe>:9030/?useSSL=false' \
"$MVN" -B verify -Pit                                 # + IT (mỗi IT tự bỏ qua nếu thiếu hạ tầng)
"$MVN" -B package -DskipTests && ./scripts/run-local.sh   # chạy java -jar local (env: ~/.config/hddt/data-api-local.env)
```

## Cấu hình chính (biến môi trường)

| Biến | Ý nghĩa |
|---|---|
| `DSE_CONTACT_POINTS`, `DSE_LOCAL_DC`, `DSE_USERNAME`, `DSE_PASSWORD`, `DSE_JOB_KEYSPACE` | DSE kho job (keyspace `datahub_hddt`); `DSE_CREATE_SCHEMA=true` tạo schema; `HDDT_DSE_REPLICATION` map replication |
| `DORIS_JDBC_URL`, `DORIS_USERNAME`, `DORIS_PASSWORD`, `DORIS_DATABASE` | Doris FE (MySQL protocol); tài khoản chỉ SELECT + OUTFILE |
| `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_EXPORT_BUCKET` | S3/MinIO của ứng dụng |
| `S3_DORIS_ENDPOINT` (+ `S3_DORIS_ACCESS_KEY/SECRET_KEY`) | S3 nhìn từ Doris BE cho OUTFILE |
| `EXPORT_ENGINE` | `OUTFILE` (mặc định) / `JDBC_STREAM` (dự phòng khi BE không tới được S3) |
| `MST_STATUS_SOURCE_SQL` | SQL trả `(MST, TRANG_THAI)`; mặc định `SELECT MST, TRANG_THAI FROM doris.NNT_HIS WHERE TD = '2100-01-01'` (bảng tạm `sql/doris/01_nnt_his_tam.sql`) |
| `APIC_GATEWAY_TOKEN_HEADER`, `APIC_GATEWAY_TOKEN_SHA256` | (tùy chọn) token bí mật APIC gắn thêm |
| `ESB_HMAC_KEY` (hoặc `TTHC_HMAC_KEY`), `ESB_ALLOWED_PARTNERS` | (TD-API-40) **Không còn X-Client-Id/X-Client-Secret** (09/11 — chỉ gọi nội bộ qua trục): bên gọi theo header `x-partner` → `hddt.security.partner-clients` (TTHC → tthc-hthue: HD1/HD3/HD5/HD7/HD8/job; TMS → tms-hthue: cập nhật hoàn thuế/job). Có khóa ⇒ y `hddt-tthc-sharing`: mọi `/hthue/**` bắt buộc `x-partner` + `x-hmac` = hex(HMAC-SHA256(khóa, `yyyyMMdd.<x-partner>`)), ngày giờ VN; thiếu X-Request-Id ⇒ tự sinh. Không khóa ⇒ **không xác thực** (WARN lúc khởi động); không gửi x-partner ⇒ `default-client` (`DEFAULT_CLIENT`, mặc định `noi-bo` đủ quyền; để trống ⇒ 401). Khóa = `tthc.hmac.key` của tthc-sharing, chỉ đặt ngoài repo (`application.yml` đọc `${ESB_HMAC_KEY:${tthc.hmac.key:}}`). `ESB_ALLOWED_PARTNERS` vd `TTHC,TMS` (trống = mọi đối tác). Tính thử: `printf '%s' "$(TZ=Asia/Ho_Chi_Minh date +%Y%m%d).TTHC" \| openssl dgst -sha256 -hmac "$ESB_HMAC_KEY"` |
| `WORKER_ENABLED`, `WORKER_CONCURRENCY` | tách pod api / worker |

Cờ nghiệp vụ (chỉ đổi khi có phê duyệt): `hddt.hd7.bra-tsuat`, `hddt.hd7.unassigned-tsuat-policy`,
`hddt.hd8.rules-confirmed`, `hddt.hd8.compare-fields`, `hddt.hd8.money-tolerance`, `hddt.tms.apply-mode`,
`hddt.mst-status.excluded-codes`, `hddt.job-codes.*`.

## Mã lỗi (đề xuất SI-37)

| HTTP | code | errorCode | Khi nào |
|---|---|---|---|
| 200/202 | OK | 0 | Thành công (sync/async) |
| 400 | BAD_REQUEST | HDDT-400 / HDDT-400-HEADER | Body/tham số sai; thiếu/sai X-Request-Id |
| 401 | UNAUTHORIZED | HDDT-401 | Thiếu/sai chữ ký x-partner/x-hmac (khi bật ESB), x-partner không khai báo, thiếu x-partner khi không có default-client, hoặc sai token gateway |
| 403 | FORBIDDEN | HDDT-403 | Bên gọi (x-partner) không có quyền API/MST/bucket |
| 404 | NOT_FOUND | HDDT-404 / HDDT-404-JOB | Không có hóa đơn / job (kể cả job của client khác) |
| 409 | CONFLICT | HDDT-409-AMBIGUOUS / HDDT-409-IDEMPOTENCY | Khóa HD3 trùng nhiều HĐ / X-Request-Id dùng lại với nội dung khác |
| 413 / 415 / 429 | … | HDDT-413 / HDDT-415 / HDDT-429 | Body quá lớn / Content-Type / quá tải |
| 501 | NOT_IMPLEMENTED | HDDT-501-MAPPING / HDDT-501-RULE | Ánh xạ/quy tắc nghiệp vụ chưa duyệt |
| 503 | SERVICE_UNAVAILABLE | HDDT-503-MST-STATUS / HDDT-503-DORIS | Thiếu nguồn trạng thái MST / Doris không khả dụng |
| 504 | GATEWAY_TIMEOUT | HDDT-504 | Truy vấn đồng bộ quá thời gian |

`mta` của job LOI dùng mã: `HDDT-400-FILE`, `HDDT-404-FILE`, `HDDT-409-DUPLICATE`, `HDDT-413`, `HDDT-500-*`, `HDDT-501-RULE`, `HDDT-503`.

## Bàn giao

`CLAUDE.md` + `docs/handoff/` (SESSION_HANDOFF, RESUME_PROMPT, session_state.json…).
