# Triển khai OpenShift (CHƯA DÙNG — hiện chạy java -jar local, TD-API-25), sizing, kiểm thử tải

## 1. Thành phần (`deploy/openshift/hddt-data-api-service.yaml` — mẫu, CHƯA áp dụng)
ConfigMap (không secret) · Secret (REPLACE_ME) · Deployment `hddt-data-api` (HTTP, `WORKER_ENABLED=false`) ·
Deployment `hddt-data-api-worker` (job) · Service · Route TLS edge + IP whitelist APIC · NetworkPolicy · PDB.
Probes: readiness `/actuator/health/readiness` (chỉ gồm DB job — Doris chậm không gỡ pod), liveness `/actuator/health/liveness`.
Metrics: `/actuator/prometheus` (`hddt.job.*`, `hddt.auth.rejected`, `hddt.worker.running`, HTTP, Hikari).

## 2. Các bước
1. DSE: keyspace `datahub_hddt` (RF ≥ 3 ở PROD; LWT cần LOCAL_QUORUM), user riêng chỉ quyền trên keyspace này.
2. Tài khoản Doris `hddt_api_ro`: `SELECT` trên `datahub_hddt.*` + nguồn trạng thái MST; quyền OUTFILE; *không* LOAD/ALTER.
3. Bucket S3 `hddt` (prefix `export/`, `import/tthc/`, `import/tms/`), lifecycle xóa `export/` sau 7–10 ngày (khớp
   `hddt.export.retention-days`), quyền: app RW, Doris BE ghi `export/query-result/`, TTHC đọc `export/`, ghi `import/tthc/`,
   TMS ghi `import/tms/`.
4. Kiểm tra mạng: **mọi Doris BE phải tới được `S3_DORIS_ENDPOINT`** (DEV 31/10: BE 192.168.1.79 timeout tới máy dev).
5. `oc create secret …` (xem đầu file yaml) → `oc apply -f deploy/openshift/hddt-data-api-service.yaml`.
6. Đăng ký API trên APIC/trục: chuyển tiếp `X-Request-Id`, `x-partner` (TTHC/TMS), `x-hmac` (khi bật ESB HMAC), gắn `X-APIC-Gateway-Token`. Không còn `X-Client-Id`/`X-Client-Secret` (TD-API-40).

## 3. Sizing khởi điểm (cần đo tải để chốt)
| Thành phần | Đề xuất | Căn cứ |
|---|---|---|
| api pod | 2 × (1–2 vCPU, 1–2 GB) | HD3/HD7 đồng bộ, I/O-bound; Tomcat 200 thread, Doris pool 20 |
| worker pod | 2 × (1–2 vCPU, 2–3 GB), concurrency 4 | OUTFILE chạy trên Doris; HD8/TMS giữ tập dấu vân tay ~100 MB / 1 triệu dòng |
| Doris | `query_timeout` xuất 7200 s; giới hạn job xuất đồng thời 4/loại toàn cụm | tránh tranh chấp với realtime/backfill — nên dùng workload group riêng |
| DSE (kho job) | dùng cụm DSE hiện có | vài nghìn job/ngày, mỗi job vài chục LWT; refund_item theo dòng TMS |

## 4. Kiểm thử tải (CHƯA chạy — NOT_RUN)
- HD3: 50 → 500 rps (k6/JMeter) với khóa thật trên môi trường TEST; đo p95, lỗi 504, CPU FE/BE.
- HD1/HD5: MST lớn (≥ 1–10 triệu HĐ/kỳ), đo thời gian OUTFILE, số file, tải BE; song song 4 job.
- HD8: file 1 triệu dòng; đo thời gian 2 lượt + số truy vấn lô (2.000 truy vấn @500).
- Kịch bản sự cố: kill pod worker giữa OUTFILE (lease 2 phút ⇒ nhận lại), Doris FE restart, MinIO không tới được.
