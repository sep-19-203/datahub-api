# Kiến trúc (tóm tắt cho bàn giao)

Xem đầy đủ: [`../architecture/ARCHITECTURE.md`](../architecture/ARCHITECTURE.md) và
[`../architecture/DEPLOYMENT_OPENSHIFT.md`](../architecture/DEPLOYMENT_OPENSHIFT.md).

- APIC → Route → pod **api** (filter xác thực → `HthueController`) → Doris (sync) hoặc `api_job` trên DSE keyspace `datahub_hddt` (async, LWT).
- Pod **worker** → `JobWorker` (LWT, lease 2 phút, heartbeat, retry 3 lần backoff 30 s→10 phút) → handler:
  xuất Doris OUTFILE → S3 (kiểm tra + manifest), đối soát HD8 theo lô, kiểm tra TMS + staging DSE + ghi sổ hoàn Doris `HD_SO_HOAN`.
- Dữ liệu: Doris `datahub_hddt` chỉ đọc (HOADON_HDR/DTL, HD_MST_LIST, HD_MST_SUMMARY); DSE `datahub_hddt`: `api_job`,
  `api_job_by_request`, `api_job_active`, `api_job_event`, `api_job_finished`, `refund_batch`, `refund_item`, `refund_applied_content`; S3 `export/query-result/<jobId>/`, `export/hd8-result/<jobId>/`,
  đầu vào `import/tthc/`, `import/tms/`.
