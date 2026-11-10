# CLAUDE.md — hddt-data-api-service

> Mọi session Claude mới PHẢI đọc file này trước khi sửa source. Trả lời người dùng bằng tiếng Việt.

## 1. Vị trí & phạm vi

- **Repo riêng** (từ 01/11): `http://192.168.1.69/hddt1/datahub_hddt/hddt-data-api-service` (nhánh `main`). Lịch sử 8 commit đầu tách từ thư mục `hddt-data-api-service/` của
  `hddt-toolkit` (nhánh `feature/hddt-data-api-service`) bằng `git subtree split` — bản đó giờ chỉ còn là lịch sử, sửa ở repo này.
- Dự án Maven độc lập (parent `spring-boot-starter-parent`), không phụ thuộc `hddt-spark-backfill`/`hddt-datahub-realtime`
  (hai module đó vẫn ở `hddt-toolkit`).
- Nhiệm vụ: 7 API trong `API HDDT - TTHC - TMS.xlsx` (HD1, HD3, HD5, HD7, HD8, TMS, GET job) đọc Doris 4.0.8
  `datahub_hddt`, xuất CSV lên S3/MinIO, job bất đồng bộ bền vững trên **DSE keyspace `datahub_hddt`** (LWT).
  Chạy local bằng `java -jar` (`scripts/run-local.sh`); OpenShift chưa dùng.

## 2. Bắt buộc trước khi code tiếp

1. Đọc `docs/handoff/SESSION_HANDOFF.md` → `docs/handoff/session_state.json` → `docs/spec/API_SPEC_ISSUES.md`
   → `docs/handoff/TECHNICAL_DECISIONS.md` → `docs/handoff/KNOWN_ISSUES.md`.
2. `git status`, `git log --oneline -10`; đối chiếu với handoff.
3. Build + test trước khi sửa (lệnh ở mục 5).

## 3. Nguyên tắc không được vi phạm

- Nguồn căn cứ: bảng field trong workbook > ví dụ curl/JSON. Không tự thêm/đổi tên field, không đổi thứ tự cột CSV
  (khóa bởi `CsvLayoutsTest`). Mâu thuẫn → `docs/spec/API_SPEC_ISSUES.md`.
- Trường tiền/trạng thái chưa có nguồn hoặc quy tắc chưa duyệt ⇒ **chặn** (501/BLOCKED), không trả 0/NULL giả:
  HD7 `bra` đã chốt 01/11 (`docs/spec/HD7_DANH_GIA_TSUAT.md` §5). Sổ hoàn TMS đã cài (Doris `HD_SO_HOAN`, 01/11). Đã chốt 31/10–01/11: HD8 (file vào = CSV HD1; so tgtcthue, tgtthue khớp tuyệt đối; trả nguyên dòng vào),
  NNT_HIS tạm (SI-24), 13 cột số tiền theo thuế suất `pct*` đã bỏ khỏi CSV HD1/HD5/HD8 (06/11, SI-51). Doris KHÔNG dùng làm kho job (đã thực nghiệm, TD-API-03b).
- Không ghi credential/secret vào repo, log, handoff. Secret mẫu trong Excel KHÔNG được chép. Test dùng secret giả
  (`TestProps`), MST giả `01000000xx`.
- Không kết nối production. Doris DEV 192.168.1.77: chỉ đọc `datahub_hddt`, trừ bảng sổ hoàn `datahub_hddt.HD_SO_HOAN` (module tạo, chỉ INSERT); bảng tạm `doris.NNT_HIS` do module tạo. `ListServingEquivalenceIT` chỉ xóa database tạm `bf_it_<số>` (do toolkit IT tạo) khi `IT_LIST_DB_DROP=true`. DSE DEV:
  keyspace `datahub_hddt` của module; IT dùng keyspace tạm. MinIO 192.168.1.69 dùng chung — IT tự xóa bucket tạm.
  Credential chỉ ở `~/.config/hddt/*.env`.
- Không push/merge/deploy khi chưa có yêu cầu.
- Kiến trúc: domain thuần Java (`domain/`), use case + port (`application/`), adapter (`adapter/web`, `infrastructure/`).
  SQL chỉ ở `infrastructure/doris` và `infrastructure/job`; input người dùng chỉ qua tham số `?`.

## 4. Bản đồ mã

| Gói | Nội dung |
|---|---|
| `domain.csv.CsvLayouts` | Bố cục CSV HD1 (15, API v3 — SI-52), HD5 (18 — thttltsuat + lddctthe theo sheet 08/11, SI-63), HD8 đầu vào/kết quả (14, theo HD1 bản 06/11 — KI-API-31), TMS (12) — kèm ô nguồn |
| `domain.hd7.SalesMappingPolicy`, `TaxRateCode` | Ánh xạ mã thuế suất → chỉ tiêu bán ra HD7 (05/11 theo GiaiPhap v3.8: 8% không ánh xạ, 32b null, 34a = ktpvdc, KHAC bỏ qua; SQL ở `infrastructure.doris.DorisSummaryAdapter`) |
| `domain.verify.*` | Quy tắc so sánh + engine đối soát HD8 |
| `application.job.*` | Submit (idempotency), query, handler contract, phân loại lỗi |
| `application.export.ListExportHandler` | HD1/HD5: xuất → kiểm tra → manifest |
| `application.verify.PurchaseVerificationHandler` | HD8: kiểm tra file 2 lượt, đối soát theo lô |
| `application.refund.RefundUpdateHandler` | TMS: kiểm tra từng dòng, staging DSE, chống hoàn trùng, ghi sổ hoàn Doris `HD_SO_HOAN`, đối chiếu COUNT |
| `infrastructure.doris.*` | SQL Doris (HD3, HD7, tra lô, OUTFILE/stream) + `DorisRefundLedgerAdapter` (ghi duy nhất vào Doris: sổ hoàn); `hddt.doris.list-serving` (TD-API-34, mặc định false) ⇒ HD1/HD5/HD8/TMS chỉ đọc HD_MST_LIST v5; HD8 một truy vấn ứng viên/job + tra theo khóa literal (TD-API-35, `hddt.hd8.candidate-index`) |
| `infrastructure.dse.*` | DSE: api_job (+by_request/active/event/finished), refund_batch/item/applied_content — mọi ghi `api_job` là LWT, timestamp server |
| `infrastructure.worker.JobWorker` | Worker poll/lease/heartbeat/retry/graceful shutdown |
| `adapter.web.*` | Controller 7 API, envelope, filter xác thực/phân quyền/rate-limit (TD-API-40: KHÔNG còn X-Client-Id/X-Client-Secret — bên gọi/quyền theo `x-partner` → `hddt.security.partner-clients`; có `ESB_HMAC_KEY`/`tthc.hmac.key` ⇒ bắt buộc chữ ký x-hmac y `HmacFilter` tthc-sharing (`EsbHmacVerifier`), không khóa ⇒ không xác thực, thiếu x-partner ⇒ `default-client`); `OpenApiConfig` (header trên Swagger), `SwaggerUiConfig` (Swagger UI tự sinh X-Request-Id khi để giá trị mẫu) |

## 5. Lệnh

```bash
# JDK 8 (từ 02/11 dự án là Java 8 + Spring Boot 2.7.18 — ../migration-reports/01-data-api-service.md).
# Build bằng JDK 9+ vẫn được: profile jdk9plus-release8 tự ép --release 8.
export JAVA_HOME=<jdk8>
MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
"$MVN" -B test                    # unit + contract
"$MVN" -B verify -Pit             # + IT (tự bỏ qua nếu thiếu IT_DSE_* / IT_S3_* / IT_DORIS_URL) — lệnh đầy đủ: SESSION_HANDOFF
./scripts/run-local.sh            # java -jar local, đọc ~/.config/hddt/data-api-local.env
```

## 6. Handoff

`docs/handoff/` (SESSION_HANDOFF, session_state.json, RESUME_PROMPT, IMPLEMENTATION_PROGRESS, TEST_RESULTS, ...).
Cập nhật sau mỗi mốc đã kiểm chứng.
