# Ma trận truy vết API ↔ đặc tả ↔ mã ↔ dữ liệu ↔ test

Nguồn: `API HDDT - TTHC - TMS.xlsx` (SHA-256 `4942682d…b524`). Ô = ô đầu vùng merge. Bảng Doris = `datahub_hddt` DDL v4.
Ký hiệu SQL: `QA` = `DorisInvoiceQueryAdapter`, `SA` = `DorisSummaryAdapter`, `LA` = `DorisInvoiceLookupAdapter`,
`EX` = `ExportSqlBuilder` + `CsvLineSql` (biểu thức dòng CSV), `MS` = `MstStatusSql`/`DorisTaxpayerStatusAdapter`.
Test: `AC` = `ApiContractTest`, `OA` = `OpenApiContractTest`, `CL` = `CsvLayoutsTest`, `ES` = `ExportSqlTest`,
`DI` = `DorisDevIT`, `SS` = `SyncServicesTest`, `VR` = `VerificationAndRefundHandlerTest`, `CC` = `CsvCodecTest`,
`RW` = `RealtimeResultWaiterTest`.

## 0. Header & envelope chung

| Field | Ô (HD1 / các sheet cùng dòng) | Mã | Quy tắc | Test |
|---|---|---|---|---|
| X-Request-Id | HD1!C8 | `ClientAuthenticationFilter` | UUID bắt buộc; = traceId; khóa idempotency (SI-36) | AC `missingRequestId_is400Header…` |
| X-Client-Id / X-Client-Secret | HD1!C9/C10 | `ClientRegistry.authenticate` | so SHA-256 constant-time với Secret; phân quyền theo route (`RoutePermissions`) | AC (401/403), `ClientRegistry` |
| Content-Type | HD1!C11 | `@PostMapping(consumes=JSON)` | sai ⇒ 415 | AC `validation_…mediaType` |
| traceId | HD1!C24 (bảng; mẫu `tracedId` SI-01) | `ApiResponse.traceId` | = X-Request-Id | AC, OA |
| status | HD1!C25 | `ApiResponse.status` | = mã HTTP (SI-02/03) | AC |
| code / errorCode / message | HD1!C26–C28 | `ErrorCode` | `OK`/`0`/`Thành công`; lỗi theo danh mục | AC |
| data | HD1!C29 | generic `T` | null khi lỗi | AC |

## 1. HD1 — `POST /hthue/v1/mua-vao` (HD1!D2–D4)

| Field | Ô | DTO / class | Nguồn / quy tắc | SQL | Test |
|---|---|---|---|---|---|
| nmmst | C14 | `PurchaseListRequest.nmmst` | `HD_MST_LIST.MST`, `CHIEU='MUA_VAO'` | EX `select` (`l.MST = ?`) | AC, ES |
| tthang / dthang | C15/C16 | `MonthRange` | `KY ∈ [đầu tháng tthang, đầu tháng dthang]`, KY = tháng(NKY→NCMA→TDLAP) | EX (`l.KY >= ? AND l.KY <= ?`) | `DomainRulesTest`, ES |
| lbttmsthue | C17 | → `ExportSpec.excludeMstStatus` | loại HĐ có TT MST **người bán** ∈ {03,05,06} (cấu hình) | EX (`s ON s.MST = h.NBMST`, `NOT IN`) | ES `hd1_…SellerStatus` |
| lbbhphap | C18 | → `excludeIllegal` | loại `BHPHAP = 1` (SI-27) | EX | ES |
| (quy tắc mẫu số) | — (GiaiPhap v3.8 R1, SI-46) | `ExportSqlBuilder.PURCHASE_FORM_RULE` | chỉ HĐ mẫu 1, hoặc mẫu 5 có `TGTTHUE ≠ 0`; cờ `hddt.hd1.form-rule-enabled` | EX (`h.KHMSHDON = 1 OR …`) | ES `hd1_appliesFormRuleR1…` |
| data.jobId…tsdong | C30–C36 | `JobStatusData` ← `DtoMapper.status` | url/tsdong chỉ khi HOAN_THANH; **< 10k dòng ⇒ POST chờ job xong và trả luôn** (SI-06, `RealtimeResultWaiter`) | EX `countUpTo` (`COUNT … LIMIT ?`) | AC `hd1_smallResult…`, RW, ES `countUpTo…`, OA |
| jobCode | Q31 | `JobType.PURCHASE_LIST` | `hthue.mua-vao` | — | `DomainRulesTest` |

> **08/11 (SI-52, API v3)**: CSV HD1 **15 cột** (HD1!C42–C70 bỏ dòng gạch ngang C47 `nky`, C53–C65 `pct*`): nbmst, khmshdon, khhdon, shdon, tdlap, tthai, nbten, tgtcthue, tgtthue, **thttltsuat** (`NULLIF(CAST(h.THTTLTSUAT AS STRING), 'null')`, JSON bọc `"`), bhphap, bhpldo, bhpngay, ttmstnban (`s.TRANG_THAI`), **lddctthe** (`h.GCHDGOC`, SI-53 đề xuất). Test: CL `hd1_v3_has15Columns…`, ES `lineExpression_hasExactly15And16…`, `listServing_hd1V3_fallsBackToHeaderJoin…`.
>
> **06/11 (SI-51)**: 13 cột `pct*` (11–23 trong bảng dưới) đã bỏ — CSV HD1 còn 14 cột: nbmst, khmshdon, khhdon, shdon, tdlap, nky, tthai, nbten, tgtcthue, tgtthue, bhphap, bhpldo, bhpngay, ttmstnban (nay là đầu vào HD8, KI-API-31). Bảng dưới giữ để đối chiếu bản cũ.

CSV HD1 (C42–C68, 27 cột cũ → 14 cột, không header) — `CsvLayouts.HD1_PURCHASE`, biểu thức `CsvLineSql.HD1_SOURCES`:

| # | Cột | Nguồn Doris | | # | Cột | Nguồn Doris |
|---|---|---|---|---|---|---|
| 1 | nbmst | `h.NBMST` (chuỗi bọc `"`) | | 15 | pct0_sotien | `h.PCT0_SOTIEN` |
| 2 | khmshdon | `h.KHMSHDON` | | 16 | pct5_sotien | `h.PCT5_SOTIEN` |
| 3 | khhdon | `h.KHHDON` | | 17 | pct5_sothue | `h.PCT5_SOTHUE` |
| 4 | shdon | `h.SHDON` | | 18 | pct8_sotien | `h.PCT8_SOTIEN` |
| 5 | tdlap | `DATE_FORMAT(h.TDLAP,'%Y-%m-%d')` | | 19 | pct8_sothue | `h.PCT8_SOTHUE` |
| 6 | nky | `DATE_FORMAT(h.NKY,'%Y-%m-%d %H:%i:%s')` | | 20 | pct10_sotien | `h.PCT10_SOTIEN` |
| 7 | tthai | `h.TTHAI` (chỉ 1,2,3,5 — C-17, SI-26) | | 21 | pct10_sothue | `h.PCT10_SOTHUE` |
| 8 | nbten | `h.NBTEN` | | 22 | pctkhac_sotien | `h.PCTKHAC_SOTIEN` (SI-22) |
| 9 | tgtcthue | `h.TGTCTHUE` DECIMAL(21,6) | | 23 | pctkhac_sothue | `h.PCTKHAC_SOTHUE` (SI-22) |
| 10 | tgtthue | `h.TGTTHUE` | | 24 | bhphap | `h.BHPHAP` |
| 11 | pctkct_sotien | `h.PCTKCT_SOTIEN` | | 25 | bhpldo | `h.BHPLDO` |
| 12 | pctkkknt_sotien | `h.PCTKKKNT_SOTIEN` | | 26 | bhpngay | `h.BHPNGAY` |
| 13 | pctcttc_sotien | **không có nguồn ⇒ rỗng** (SI-08) | | 27 | ttmstnban | `s.TRANG_THAI` (nguồn cấu hình, SI-24) |
| 14 | pctktpvdc_sotien | **không có nguồn ⇒ rỗng** (SI-08) | | | | |

Test: CL (thứ tự), ES (14 phần tử, quoting), `ListExportHandlerTest` (đếm bản ghi/cột trước khi công bố), DI (EXPLAIN + chạy thật), smoke HTTP (TEST_RESULTS §3).

## 2. HD3 — `GET /hthue/v1/chi-tiet` (HD3!D2–D4)

| Field | Ô | DTO / class | Nguồn / quy tắc | SQL | Test |
|---|---|---|---|---|---|
| nbmst, khmshdon, khhdon, shdon, tdlap (query) | C13–C17 | `HthueController.detail` | khmshdon chuỗi 1 chữ số (SI-21); khhdon ≤ 11 byte; biến thể Đ/Ð (SI-18) | QA `findHeaders`: `HD_MST_LIST` (KY = tháng tdlap, MST, BAN_RA, TDLAP, SHDON, KHHDON IN) ⨝ `HOADON_HDR` (TDLAP, NBMST, KHMSHDON, SHDON, KHHDON) | AC, SS, DI |
| data.nbmst…tgtthue | C28–C37 | `InvoiceDetailData` | `h.NBMST, KHMSHDON, KHHDON, SHDON, TDLAP, NKY, TTHAI, NBTEN, TGTCTHUE, TGTTHUE` | QA | AC, DI |
| thttltsuat[].tsuat/thtien/tthue | C38–C40 (+E55) | `TaxRateDto` | `CAST(h.THTTLTSUAT AS STRING)` → mảng giữ nguyên (SI-20); NULL ⇒ `[]` | QA `taxLines` | AC, DI |
| ttmstnban | C41 | `InvoiceDetail.ttmstnban` | nguồn TT MST cấu hình; chưa cấu hình ⇒ 503 (SI-24) | MS `findStatus` | SS, AC |
| bhphap, bhpldo, bhpngay | C42–C44 | | `h.BHPHAP, BHPLDO, BHPNGAY` | QA | AC |
| dshhdvu[].ten/thtcthue/ltsuat/tthue | C45–C49 | `GoodsDto` | `HOADON_DTL.TEN, THTCTHUE(varchar→số), LTSUAT, TTHUE(varchar→số)` (SI-34) — 1 truy vấn theo khóa header | QA `findGoodsLines` | SS, DI |
| 404 / 409 | — | `InvoiceDetailService` | không thấy ⇒ 404; >1 HĐ (INV+MTT…) ⇒ 409, không chọn dòng đầu | — | SS, AC |

## 3. HD5 — `POST /hthue/v1/ban-ra` (HD5!D2–D4)

Body như HD1 với `nbmst` (C14) ⇒ `HD_MST_LIST.MST`, `CHIEU='BAN_RA'`, KY = tháng TDLAP, cắt partition `h.TDLAP ∈ [đầu tthang, đầu tháng sau dthang)`;
`lbttmsthue` (C17) ⇒ trạng thái MST **người mua** (`s ON s.MST = h.NMMST`); `lbbhphap` (C18) ⇒ loại `BHPHAP = 1`.
Quy tắc mẫu số R1 (SI-47): `ExportSqlBuilder.FORM_RULE_R1`, cờ `hddt.hd5.form-rule-enabled`. jobCode `hthue.ban-ra` (Q31).
`data.url`/`tsdong` (AA35/AA36): < 10k dòng ⇒ POST chờ job xong và trả luôn (SI-06, `RealtimeResultWaiter`, `hddt.hd5.realtime-*`).
CSV **18 cột** (Google Sheet sửa 08/11 09:36, HD5!C42–C72 — SI-63): 13 dòng pct* C55–C67 gạch (SI-51), số tiền theo thuế suất
gộp vào `thttltsuat` (C53 hết gạch — đảo SI-48 cho HD5); `specCell` bỏ qua các dòng gạch nên trỏ đúng ô — `CsvLayouts.HD5_SALES`,
`CsvLineSql.HD5_SOURCES`: như HD1 trừ: 8 `nmten` ← `h.NMTEN` (SI-39), 9 `nmmst` ← `h.NMMST`, 12 `thttltsuat` ←
`NULLIF(CAST(h.THTTLTSUAT AS STRING), 'null')` (JSON bọc nháy, như HD1 v3), 13 `tgtttbso` ← `h.TGTTTBSO`, 14–16 `bhphap`,
`bhpldo`, `bhpngay` ← `h.BHP*`, 17 `ttmstnmua` ← `s.TRANG_THAI` (người mua không MST ⇒ rỗng, SI-25), 18 `lddctthe` (C72, String, N)
← `h.GCHDGOC` như HD1/HD3 (SI-53). `thttltsuat`, `lddctthe` chỉ ở HDR ⇒ HD5 không dùng list-serving (KI-API-32).
Test: CL, ES `hd5_…`, `formRuleR1_…`, `hd5_countUpTo…`, `listServing_hd5V0811…`, AC `hd5_smallResult…`, RW `hd5_…`,
DI `hd5_exportEndToEnd_jdbcStream…`, `hd5_exportEndToEnd_outfile…` (08/11 PASS trên DEV, kiểm 18 cột), smoke HTTP.

## 4. HD7 — `POST /hthue/v1/goi-y` (HD7!D2–D4)

| Field | Ô | Nguồn / quy tắc | SQL | Test |
|---|---|---|---|---|
| mst, tthang, dthang | C14–C16 | MonthRange (≤ `hddt.hd7.max-months`) | — | AC |
Phạm vi chung (04/11): `HD_MST_LIST` (CHIEU, IS_ELIGIBLE=1) ⨝ `HOADON_HDR` + R1 mẫu số (SI-54); gom theo mã thuế suất:
THTTLTSUAT ⇒ dòng hàng HOADON_DTL.LTSUAT (TD-API-29) ⇒ suy từ tỷ lệ thuế/giá trị header (SI-57). Không dùng `HD_MST_SUMMARY`.
05/11 (TD-API-38): kỳ theo ngày lập, HĐ thay thế/điều chỉnh theo `TDLHDGOC` — cả hai chiều (SI-59, `period-basis`);
số tiền × tỷ giá khi DVTTE ≠ VND (SI-60); HĐ điều chỉnh thiếu THTTLTSUAT đóng góp 0 (SI-61).

| Field | Ô | Nguồn / quy tắc | SQL | Test |
|---|---|---|---|---|
| mvao.tgtmvao (23) / tgttmvao (24) | C29/C30 | Σ thtien / tthue của mã `hddt.hd7.mvao-tsuat` = `0%, 5%, 10%` (SI-55) trên phạm vi MUA_VAO (cắt TDLAP theo MIN/MAX của danh sách) | SA `breakdown(MUA_VAO)` (4 SQL) | SS `hd7_purchasesOnly0510…`, `SummarySqlTest`, DI |
| (kỳ mua vào) | — | `NGAY_LAP`: ngày lập / ngày lập HĐ gốc (v3.8 R4) — khác HD1 (NKY→NCMA→TDLAP, KI-API-35) | SA `boundsSql`, `listFilter` | `SummarySqlTest.periodNgayLap…` |
| mvao.slmvao | C31 | Số HĐ mua vào trong phạm vi sau R1 (SI-55) | SA `headerGroupsSql` | SS, DI |
| bra.tgtbr0 (29) / tgtbr5 (30) / tgttbr5 (31) | C33–C35 | mã tsuat `0%` / `5%` (thtien) / `5%` (tthue) trên phạm vi BAN_RA (cắt TDLAP theo tháng) | SA `breakdown(BAN_RA)` (3 SQL) + `SalesMappingPolicy` | `SalesMappingPolicyTest`, `SummarySqlTest`, DI |
| bra.tgtbr10 (32) / tgttbr10 (33) | C36/C37 | `10%`; 8% không ánh xạ (SI-56, GiaiPhap v3.8 Q4) | SA | idem |
| bra.tgtbrkct (26) / tgtbrkkknt (32a) | C38/C39 | `kct` / `kkknt` | SA | idem |
| bra.tgtbrktgtt (32b) / tgtbrktpvdc (34a) | C40/C41 | 32b: không có trong v3.8 ⇒ **null**; 34a: mã `ktpvdc` (SI-58, 05/11); KHAC bỏ qua + WARN | SA | SS `hd7_purchasesOnly0510…`, AC `hd7_fieldsWithoutSource…` |
| data.lddctthe | C42 (Level 1, không Parent) | String, N — đặt trong `data`; chưa có nguồn/quy tắc gộp ⇒ **null** (SI-64, 08/11) | — | AC `hd7_fieldsWithoutSource…` |

## 5. HD8 — `POST /hthue/v1/mua-vao/kiem-tra` (HD8!D2–D4)

| Field | Ô | Mã | Quy tắc | Test |
|---|---|---|---|---|
| nmmst | C14 | `PurchaseVerificationRequest` | `HD_MST_LIST.MST`, MUA_VAO | VR |
| tngay / dngay | C15/C16 | `DateFormats.parseDate` | giới hạn **TDLAP** (`l.TDLAP`, `h.TDLAP` ∈ [tngay, dngay]); dòng ngoài khoảng ⇒ lỗi đầu vào (SI-33) | VR |
| s3url | C17 | `S3UrlPolicy` | s3:// + bucket/prefix whitelist, '*' cuối = prefix (SI-07) | `S3UrlPolicyTest`, AC |
| jobCode | C30 | `JobType.PURCHASE_VERIFY` | `hthue.mua-vao.kiem-tra` (SI-04) | `DomainRulesTest` |
| File vào | — (MISSING) | `InvoiceRecordParser` (= bố cục HD1, SI-12) | lượt 1 kiểm tra toàn bộ; lỗi ⇒ LOI | VR, CC |
| Đối soát | A35 | `LA.indexPurchases` (1 truy vấn ứng viên/job, DB-P1-02 TD-API-35) + `LA.fetchPurchases` (mỗi lô theo khóa literal); file nhỏ/người mua lớn hoặc `hddt.hd8.candidate-index=false` ⇒ `LA.findPurchases` (1 truy vấn/lô) + `ReconciliationEngine` | khóa nbmst+khmshdon+khhdon+shdon; so **tgtcthue, tgtthue**, sai số 0 (SI-13 chốt 01/11) | `ReconciliationEngineTest`, VR, DI |
| File kết quả 14 cột (06/11) | C37–C63 | `InvoiceRecordCsv.inputLine`, `CsvLayouts.HD8_RESULT` | chỉ dòng không hợp lệ, **nguyên dòng đầu vào** (SI-14 chốt 01/11) | VR, CL |

## 6. TMS — `POST /hthue/v1/cap-nhat-hoa-don-hoan-thue` (TMS!D2–D4)

| Field | Ô | Mã | Quy tắc | Test |
|---|---|---|---|---|
| s3url | C14 | `S3UrlPolicy` (prefix `import/tms/`) | | AC |
| status | Q21 | `hddt.api.tms-accepted-http-status` (200) | SI-03 | AC |
| data.jobId/jobCode/tthai | C26–C28 | `JobAckData`; `hthue.cap-nhat` | | AC, OA |
| CSV vào 12 cột | C34–C45 | `RefundRowParser`, `CsvLayouts.TMS_REFUND_INPUT` | thttltsuat = JSON mảng (SI-16) | CC, VR |
| Đối chiếu | — | `LA.findRefundTargets` (gom theo tháng, BAN_RA; lấy LOAI_HD, CQT, ID, NMMST) | VALID / NOT_FOUND / AMBIGUOUS / DUPLICATE / INVALID → `refund_item` | VR, `DseJobStoreIT` |
| Ghi sổ hoàn | — (thiết kế 01/11) | `RefundLedgerPort` → `DorisRefundLedgerAdapter` (`HD_SO_HOAN`), staging `DseRefundStore.forEachValidItem` | LEDGER mặc định; chống hoàn trùng; đối chiếu COUNT theo MCV (SI-15) | VR, `DseJobStoreIT`, `DorisRefundLedgerIT` |

## 7. GET job — `GET /hthue/v1/job/{jobId}` (JOB!D2–D4)

| Field | Ô | Nguồn | Test |
|---|---|---|---|
| jobId (path) | C14 | `api_job.job_id`; không phải UUID / của client khác ⇒ 404 | `JobServicesTest`, AC |
| jobId, jobCode, tthai | C26–C28 | `api_job.job_id, job_code, state→PublicJobStatus` | AC, `DseJobStoreIT` |
| mta | C29 | `api_job.description` (lỗi: mô tả an toàn, không lộ nội bộ) | `JobWorkerTest` |
| tdkxuat | C30 | `api_job.created_at` (giờ VN, SI-29) | AC |
| url / tsdong | C31/C32 | `result_url`, `row_count` — chỉ khi SUCCEEDED & chưa hết hạn | AC, smoke |
