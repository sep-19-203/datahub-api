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

### Kết quả job gửi TTHC và TMS qua Kafka (working tree 10/11/2026)

`JobWorker` chốt `SUCCEEDED`/`FAILED` thành công rồi gọi `JobResultPublisher` gửi trực tiếp XML theo
`Chuan_XML_nhan_KQ_JOB.docx` lên Kafka internal `datahub-job-result-out` (key = `jobId`), đợi broker xác nhận.
Không lưu trạng thái gửi/hàng chờ vào DSE, không có DDL mới hay tác vụ poll Kafka. Kho job DSE hiện có vẫn phục vụ
xử lý job; khi gửi đọc lại đúng một job theo ID để lấy trạng thái/kết quả cuối. Job `RETRY_WAIT` hoặc mất lease không gửi.
Job của client `tthc-hthue` và `tms-hthue` cùng đi topic này theo quyết định của Sếp 10/11; `ReceiptCode` phân biệt đích.
Job TMS thành công có `TSDong` nhưng không có `URL` vì chỉ ghi sổ hoàn. GET job vẫn dùng để tra cứu.
Lỗi gửi chỉ ghi log, không đổi trạng thái job, không tự gửi lại ở tầng ứng dụng; process dừng sau khi chốt job có thể
không phát được bản tin. Kafka producer có retry/idempotence trong phiên, không thay thế hàng chờ bền vững.

- **Trước khi bật:** cấp kết nối Kafka nội bộ (bootstrap, SASL nếu cụm yêu cầu) và quyền ghi topic cho service; không cần tạo bảng gửi kết quả.
- **Trục internal như luồng TTHC của HDDT** (Sếp chốt 10/11): `JobResultPublisher` dùng đích danh `kafkaInternalTemplate`,
  đọc cùng property với `hddt-registration.KafkaInternalConfig`: `hddt.kafka.internal.bootstrap-servers`,
  `hddt.kafka.security-protocol`, `hddt.kafka.sasl.mechanism`, `hddt.kafka.internal.sasl.jaas-config`.
  Chỉ đổi topic kết quả thành `datahub-job-result-out`; không chuyển sang Kafka external.
- Gửi Kafka **bật mặc định** (`hddt.job-result.enabled=${JOB_RESULT_ENABLED:true}`, Sếp chốt 10/11).
  `JOB_RESULT_ENABLED=false` vẫn ghi đè để tắt; cấu hình ngoài JAR cần đổi nếu còn đặt false.
  `application.yml` đã có mặc định **UAT internal ngày 10/11/2026**: broker `10.64.111.76:9093`,
  `SASL_PLAINTEXT`, `SCRAM-SHA-256`; môi trường khác phải ghi đè
  `KAFKA_INTERNAL_BOOTSTRAP_SERVERS`, `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM` tương ứng.
  Sếp chốt tiếp 10/11: `hddt.kafka.internal.sasl.jaas-config` khai báo trực tiếp trong YAML như bản realtime UAT;
  JAR build từ cấu hình này chứa credential UAT. Không in JAAS hoặc chép giá trị vào tài liệu/log.
  `KAFKA_INTERNAL_SASL_JAAS_CONFIG` cũ không còn được YAML tham chiếu; broker/protocol/mechanism vẫn cho env override.
  Dùng cùng broker/security internal đã cấp cho HDDT trong đúng môi trường.
  Biến chung cũ `KAFKA_BOOTSTRAP_SERVERS` không chọn được đích producer này.
  `JOB_RESULT_TOPIC` mặc định `datahub-job-result-out`;
  `JOB_RESULT_CLIENT_ID` mặc định `tthc-hthue`, `JOB_RESULT_TMS_CLIENT_ID` mặc định `tms-hthue`.
- Mặc định UAT: `JOB_RESULT_SEND_CODE=HDDT`, `JOB_RESULT_TTHC_RECEIPT_CODE=TTHC`, `JOB_RESULT_TMS_RECEIPT_CODE=TMS`;
  cả ba vẫn có thể ghi đè qua env. Thiếu/rỗng giá trị hiệu lực khi bật sẽ báo lỗi startup.
  Tên TTHC đã đổi từ `JOB_RESULT_RECEIPT_CODE` / `hddt.job-result.receipt-code` sang
  `JOB_RESULT_TTHC_RECEIPT_CODE` / `hddt.job-result.tthc-receipt-code`; cấu hình triển khai phải đổi theo, không dùng alias cũ.
  Đề xuất mã gửi `HDDT` dựa vào `hddt-registration.TTHCServiceImpl` (`MNGui`); hai mã nhận cần TTHC/TMS xác nhận.
  Sếp chốt dùng cùng XML `TTHCDLieu` cho cả hai bên; `TTJob=HOAN_THANH` ⇒ `KQKTra.TThai=1`, `KQKTra.MTa=Thành công`;
  `TTJob=LOI` ⇒ `KQKTra.TThai=0`, `KQKTra.MTa=Không thành công` (chốt annotation 10/11).
  Khi `LOI`, `URL`/`TSDong` bị lược bỏ; `KQJob.MTa` vẫn là mô tả lỗi cụ thể của job.
- `TGGui` là lúc gửi; `TDKXuat` lấy `finished_at` của job theo giờ Việt Nam. Đây là điểm khác GET job hiện dùng `created_at`
  cho trường cùng tên; cần TTHC xác nhận ý nghĩa trước khi bật.
- **Một topic, hai bên nhận trực tiếp:** dùng hai consumer group khác nhau; mỗi group đọc cả hai loại bản tin và lọc theo
  `ReceiptCode` trước khi xử lý. ACL Kafka chỉ chặn theo topic, không cách ly dữ liệu từng bên trong topic chung; cần bên
  tích hợp chấp nhận phạm vi nhìn thấy này hoặc dùng một trục nhận chung để định tuyến tiếp.
- **Thứ tự chuyển luồng:** TTHC/TMS xác nhận XML và subscribe topic mới (dedupe `MJob`) → cấp Kafka ACL →
  bật `JOB_RESULT_ENABLED=true` trên mọi pod chạy worker và cấu hình mã gửi/nhận → test job đạt/lỗi cho cả hai bên,
  kiểm offset của từng group → các bên mới ngừng gọi GET job để chờ. Job kết thúc khi cờ còn tắt không phát Kafka;
  không hồi tố tự động. Một lần gửi giữ worker slot trong lúc chờ broker (get timeout 10s, send có thể chờ metadata/buffer).

### Đọc log khi job xong nhưng chưa thấy kết quả Kafka

Log mới ở mức INFO/WARN/ERROR, không cần bật DEBUG toàn Kafka. Theo dõi đúng process/pod worker xử lý job.

| Marker | Ý nghĩa / kiểm tra tiếp |
|---|---|
| `JOB_RESULT_WORKER` | In `workerId`, publisher có được inject không, giá trị hiệu lực `hddt.job-result.enabled`, active profiles |
| `JOB_RESULT_DISABLED` hoặc `reason=publisher_absent` | Worker không có publisher; kiểm cờ `JOB_RESULT_ENABLED=true` trên đúng worker và bản JAR triển khai |
| `JOB_RESULT_KAFKA_CONFIG` | Broker/security protocol/SASL mechanism đang dùng; chỉ log JAAS có cấu hình hay không, không log credential |
| `JOB_RESULT_PUBLISHER_READY` | Topic và mapping client → ReceiptCode đã khởi tạo |
| `JOB_HANDLER_DONE` → `JOB_FINALIZE_BEGIN` → `JOB_FINALIZE_END applied=true` | Handler đã trả kết quả và repository đã trả xác nhận chốt job; log xuất CSV trước đó chưa chứng minh đã chốt |
| `reason=terminal_not_confirmed` | Chưa nhận xác nhận chốt job: kiểm outcome, mất lease/retry hoặc lỗi DSE sau LWT |
| `JOB_WORKER_UNHANDLED` | Lỗi thoát worker task trước đây có thể bị Future giữ im lặng; log kiểu lỗi và thông điệp đã mask |
| `JOB_RESULT_LOAD` → `JOB_RESULT_LOADED` | Đọc kết quả cuối của job trước gửi; in state/client/số dòng, chỉ in cờ URL có hay không |
| `reason=unmapped_client` | Client lưu trong job không khớp hai client cấu hình; kiểm x-partner/mapping khi tạo job |
| `JOB_RESULT_SEND_BEGIN` | Bắt đầu gọi Kafka; không in XML/URL hay danh sách hóa đơn |
| `JOB_RESULT_BROKER_ACK` | Broker xác nhận; dùng topic/partition/offset để đối chiếu UI/consumer, chưa chứng minh bên nhận đã xử lý |
| `JOB_RESULT_FAILED` / `JOB_RESULT_INTERRUPTED` | Chưa lấy được xác nhận broker hoặc đọc job/dựng XML lỗi; kiểm marker trước đó và causeType. Timeout không khẳng định bản tin không thể tới broker sau đó |

UAT 10/11, job `e9bb1e4d-52f5-4608-a23d-6cf2bd8ddfe9`: COUNT trả nhanh timeout 30s chỉ làm POST trả trạng thái;
Sếp xác nhận GET sau đó báo hoàn thành, handler log xuất 55 dòng/1 file. Bản trước không log gửi thành công hoặc bỏ qua publisher
nên chưa thể kết luận Kafka lỗi/cờ tắt từ sự vắng log. Bản log mới phải build/deploy trước; job cũ không tự phát lại.

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
