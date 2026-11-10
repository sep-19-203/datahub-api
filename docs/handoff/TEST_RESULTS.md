# Kết quả kiểm thử (cập nhật 2026-11-01, vòng 5)

**PASS** (đã chạy, đạt) · **FAIL** · **NOT_RUN** · **BLOCKED**. Temurin 21.0.13, Maven 3.9.9. Dữ liệu giả (MST `01000000xx`)
trừ truy vấn CHỈ ĐỌC trên Doris DEV (không in giá trị NNT).

## 1. Unit + contract — `mvn -B verify -Pit` (surefire): **93/93 PASS**
Như vòng 1 (xem git `4e23f6b`) + `CsvLayoutsTest.hd8Input_isHd1Structure…`; `CsvCodecTest` cập nhật cho đầu vào HD8 chỉ bắt
buộc khóa + tdlap. Lớp: DomainRules 7, CsvLayouts 5, SalesMappingPolicy 5, ReconciliationEngine 6, CsvCodec 8, S3UrlPolicy 4,
JobFailures 2, JobServices 5, SyncServices 6, ListExportHandler 5, VerificationAndRefundHandler 7, ExportSql 10, JobWorker 5,
ApiContract 12, OpenApiContract 4, DeploymentManifest 2.

## 2. Tích hợp — `mvn -B verify -Pit` (failsafe): **14/14 PASS, 0 bỏ qua**

| Test | Hạ tầng | KQ | Bằng chứng |
|---|---|---|---|
| `DseJobStoreIT` ×5 | DSE DEV (DC1, 2 node), keyspace tạm `datahub_hddt_it_*` tạo bằng `cql/V1__job_store.cql`, xóa sau test | PASS | LWT chống trùng request; 8 luồng × 24 job không job nào bị nhận 2 lần; lease hết hạn ⇒ attempt+1, pod cũ bị rào (renew/succeed false); RETRY_WAIT không nhận sớm; repository mới đọc lại đúng; refund_item theo lần thử/bucket; ghi sổ hoàn trùng nội dung bị chặn |
| `S3ObjectStorageIT` ×4 | MinIO 192.168.1.69 (2022-01-08), bucket tạm, xóa sau test | PASS | 3 file multipart ~20 MB, file rỗng, abort dọn sạch (qua xóa từng object), UTF-8 |
| `DorisDevIT` ×5 | Doris DEV 4.0.8 (chỉ đọc) + MinIO 192.168.1.69 | PASS | mọi SQL adapter; EXPLAIN LIST tablets 1/48; HD3 đúng 1 HĐ; HD5 JDBC_STREAM; **HD5 OUTFILE thật**: BE ghi `…/<jobId>/<queryId>_0.csv`, url `…_*`, đếm 29 cột |

Lỗi thật phát hiện nhờ IT và đã sửa: (1) DSE — ghi thường timestamp client "đè" LWT ⇒ job bị nhận lặp (TD-API-22);
(2) MinIO 2022 bắt buộc Content-MD5 cho DeleteObjects (TD-API-23); (3) Spring không chọn được constructor `DseJobRepository`.

## 3. Thực nghiệm Doris làm kho job (DB tạm `api_jobprobe_tmp`, đã xóa) — KẾT LUẬN: KHÔNG DÙNG ĐƯỢC
INSERT trùng UNIQUE KEY: ghi đè (state QUEUED → RUNNING của "intruder"); UPDATE có điều kiện sai: affected 0;
**2 phiên `UPDATE … WHERE state='QUEUED'` đồng thời: 15/15 lần cả hai báo affected=1**, attempt cuối = 1 (mất cập nhật).

## 4. Chạy local `java -jar` (`scripts/run-local.sh`, profile local) + HTTP end-to-end — PASS
DSE DEV (tạo keyspace `datahub_hddt`, RF DC1:2) + Doris DEV + MinIO 192.168.1.69 + **EXPORT_ENGINE=OUTFILE**:
- Khởi động 48 s (lần đầu tạo schema DSE), readiness UP.
- HD5 → `HOAN_THANH`, `url=s3://hddt/export/query-result/<jobId>/6ca1f91efdce4fef-8f97f816bf43fc7c_*`, `tsdong=1`.
- HD1 → `HOAN_THANH`, file 27 cột không header.
- **HD8 với chính file HD1** (copy sang `import/tthc/…`) → `HOAN_THANH`, "kiểm tra 1, lệch 0", `tsdong=0`.
- **HD8 file đã sửa** (tgtcthue +1 đồng, thêm 1 số HĐ không tồn tại) → "lệch 2 (không tồn tại 1, sai lệch 1)", file 27 cột:
  dòng lệch mang giá trị Doris, dòng không tồn tại chỉ có khóa.
- HD3 → 200; HD7 → 501 HDDT-501-MAPPING; TMS (client TMS) → kiểm tra 1 HĐ hợp lệ → `LOI` "ghi sổ hoàn chưa được phê duyệt (SI-15)".
- File thử trong `import/` đã xóa; tắt graceful.

## 4b. Quy tắc HD8 mới (01/11) — PASS
Unit: `ReconciliationEngineTest` (+ trường ngoài quy tắc không làm lệch), `VerificationAndRefundHandlerTest` (dòng ra = nguyên
dòng vào, số giữ nguyên văn `500001`). Tổng **94/94 PASS**. HTTP trên DEV (`run-local.sh`, OUTFILE): file HD1 nguyên vẹn → lệch 0;
đổi tên người bán + tthai → lệch 0; tgtthue +1 đồng + 1 HĐ không tồn tại → lệch 2, giá trị 2 dòng ra trùng khớp 2 dòng vào, 27 cột.

## 4c. Vòng 4 (01/11) — sổ hoàn TMS
| Hạng mục | KQ | Bằng chứng |
|---|---|---|
| Unit + contract `mvn -B test` | **97/97 PASS** | +6 test luồng sổ hoàn trong `VerificationAndRefundHandlerTest`: danh sách hợp lệ ghi theo khối + đối chiếu; HĐ đã hoàn ở MCV khác ⇒ cả lô LOI `HDDT-409-DA-HOAN`; cùng nội dung đã giữ chỗ ở job khác ⇒ trùng; COUNT lệch ⇒ `HDDT-500-LEDGER`; không ghi một phần; BLOCKED/VALIDATE_ONLY không chạm sổ |
| `DseJobStoreIT` ×5 (DSE DEV, keyspace tạm, V1+V2) | **PASS** | staging refund_item (cột mới), `forEachValidItem` qua nhiều bucket, `reserveContent` LWT trả về job giữ chỗ |
| `S3ObjectStorageIT` ×4 | **PASS** | không đổi |
| Tạo `datahub_hddt.HD_SO_HOAN` trên Doris DEV (DDL `02_hd_so_hoan.sql`) | **PASS** | bảng rỗng, `SELECT count(*)` = 0 |
| `DorisRefundLedgerIT` (DB tạm `api_it_sohoan_*`) | **BLOCKED** (môi trường) | CREATE DATABASE/TABLE + `INSERT … WITH LABEL` **thành công**; `SELECT COUNT` lỗi `MEM_ALLOC_FAILED` tại BE 192.168.1.78 (tiến trình 12,75 GB > giới hạn 12,54 GB). DB tạm đã xóa (`SHOW DATABASES LIKE 'api_it%'` = 0) |
| `DorisDevIT` ×5 | **BLOCKED** (môi trường) | cùng lỗi BE .78 (vòng 2 đã PASS; vòng 4 chỉ đổi `findBySellerKeys` → `findRefundTargets`) |
| HTTP E2E TMS → sổ hoàn | **NOT_RUN** | chờ Doris DEV |

## 4d. Vòng 5 (01/11) — HD7 theo chốt
| Hạng mục | KQ | Bằng chứng |
|---|---|---|
| Unit + contract `mvn -B test` | **104/104 PASS** | `SalesMappingPolicyTest` 8 (chuẩn hóa mã, 8%→32/33 thuế thật, 32b/34a null, KHAC bỏ qua/chặn theo chính sách, cộng trùng, thiếu trường cấu hình); `SyncServicesTest` HD7 2; `ApiContractTest` null được ghi rõ trong JSON; `SummarySqlTest` 3 (cấu trúc SQL, số tham số, không LEFT JOIN) |
| Hàm Doris trên DEV (truy vấn hằng, không chạm bảng) | **PASS** | `explode_json_array_json` + `get_json_string` → DECIMAL: '', NULL, '[]', 'null', không phải mảng ⇒ không dòng, không lỗi; 1e-06, 1e+20, chuỗi số OK; `CAST(json_extract … AS DECIMAL)` sai chữ số cuối (double) ⇒ không dùng |
| `EXPLAIN` 3 câu SQL HD7 trên Doris DEV | **PASS** | LIST 2/96 tablet; HDR/DTL nhận runtime filter (ID, IDHDON); hint `leading(d {h l})` "Used"; bản LEFT JOIN đầu tiên bị loại vì DTL làm phía build |
| Chạy thật `salesBreakdown` trên DEV | **BLOCKED** | `MEM_ALLOC_FAILED` BE 192.168.1.78 (KI-API-20) |

## 4e. NNT_HIS / NNT_HKD theo `Doris_map DKT.xlsx` (01/11)
| Hạng mục | Doris local 127.0.0.1 (1 BE) | Doris DEV 192.168.1.77 (3 BE, 3 bản sao) |
|---|---|---|
| Tạo `doris.NNT_HIS` (13 cột) + `doris.NNT_HKD` (20 cột) | PASS | PASS — bảng cũ đổi tên `NNT_HIS_OLD_20261101`; `DESC` 33 cột giống hệt local |
| Nạp 8 dòng giả NNT_HIS | PASS | PASS (lần thử 1) |
| `INSERT OVERWRITE NNT_HKD SELECT … FROM NNT_HIS` | PASS — 2 HKD (0300, 0310), cột tách từ DOC VARIANT bằng `JSON_EXTRACT_STRING` đúng | BLOCKED — đọc NNT_HIS chạm BE .78 (`MEM_ALLOC_FAILED`, 8/8 lần); đã nạp đúng 2 dòng kết quả của local bằng `INSERT … VALUES` |
| Truy vấn trạng thái MST của API (`TD = '2100-01-01'` trên DATETIME) | PASS (7 MST hiện hành) | PASS theo từng MST (0100000002/06/09/10, chạm 1 tablet); quét toàn bảng BLOCKED (.78) |

## 4f. Sau khi restart BE .78/.77 (01/11)
Nguyên nhân KI-API-20: RSS BE .78 12,82 GB / .77 12,18 GB > `mem_limit=40%` (12,54 GB), ~12 GB "UntrackedMemory" (jemalloc resident
chỉ 2,5 GB; không truy vấn/load nào chạy; đỉnh memtable 7,69 GB từ đợt nạp lớn); THP `always` trên host (log BE cảnh báo).
Restart BE .78 rồi .77 qua SSH (khóa) ⇒ RSS 1,32 / 1,42 GB.
| Hạng mục | KQ | Bằng chứng |
|---|---|---|
| `mvn -B verify -Pit` toàn bộ | **105 unit + 15 IT PASS** | `DorisRefundLedgerIT` 1, `DorisDevIT` 5 (3 SQL HD7 chạy thật, OUTFILE), `DseJobStoreIT` 5, `S3ObjectStorageIT` 4; DB tạm đã xóa |
| `NNT_HKD` làm mới bằng `INSERT OVERWRITE … SELECT` trên DEV | **PASS** | 2 dòng, khớp local |
| HTTP E2E TMS (`run-local.sh`, HĐ DEV duy nhất C2025TAJ/3337) | **PASS** | (a) hợp lệ ⇒ HOAN_THANH "Đã ghi sổ hoàn 1 hóa đơn", 1 dòng HD_SO_HOAN (MCV = jobId, DA_HOAN, NGUON_MSTHSO=HD_NMMST, STT_DONG 1, NGUON_FILE, CLIENT_ID); gửi lại cùng X-Request-Id ⇒ cùng jobId; (b) file khác cùng HĐ ⇒ LOI `HDDT-409-DA-HOAN`, không ghi thêm. Đã xóa dòng thử + file MinIO |
| HTTP HD7 cho MST bán ra 07/2025 | **PASS sau sửa** | Lần 1 trả toàn 0 **không cảnh báo**: THTTLTSUAT là VARIANT chứa JSON null (`IS NULL`=false, CAST=NULL) ⇒ HĐ lọt cả hai nhánh. Sửa: "có thuế suất" ⇔ `COALESCE(TRIM(CAST(… AS STRING)),'') LIKE '[%{%'`, nhánh thiếu = NOT(...). Lần 2: HĐ (không có dòng DTL) vào nhóm không xác định 252.275.509/25.227.551 + WARN; 32b/34a null |

## 5. Chưa chạy
| Hạng mục | Trạng thái | Lý do |
|---|---|---|
| Kiểm thử tải | NOT_RUN | Chưa có dữ liệu lớn |
| Đo hiệu năng HD7 nhánh DTL với MST nhiều HĐ | NOT_RUN | DEV chỉ còn 1 HĐ (KI-API-23) |
| Lọc `lbttmsthue` với trạng thái MST thật | BLOCKED | NNT_HIS tạm chỉ có MST giả (SQL lọc đã chạy trên DEV) |
| Testcontainers | NOT_RUN | Không có Docker; IT dùng hạ tầng DEV |
