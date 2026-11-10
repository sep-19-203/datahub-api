# Phân tích đặc tả API — `API HDDT - TTHC - TMS.xlsx`

> **Cập nhật 06/11 (SI-51):** 13 trường `pct*_sotien/sothue` đã bị gạch khỏi đặc tả HD1/HD5/HD8 và đã bỏ khỏi mã. Nội dung về các cột này dưới đây chỉ còn giá trị lịch sử.

Nguồn: `/Volumes/WORK/TECAPRO/HDDT_2026/GiaiPhapAPI/API HDDT - TTHC - TMS.xlsx`
SHA-256 `4942682d0feea668cc1731d7a7f8ddd82dcf81e0493c962737b24d8414d8b524`, 85.003 B, mtime 2026-10-31 20:54
(lúc đọc file đang mở trong Excel — có `~$API HDDT - TTHC - TMS.xlsx`; checksum là bản đã lưu).
Đọc bằng openpyxl, **toàn bộ ô có giá trị của cả 7 sheet** (382 dòng dump; không có công thức/defined name).
Tọa độ ô dưới đây là ô đầu của vùng merge (vd. `C14` = vùng `C14:G14`).

Quy ước bố cục mọi sheet: `A1` "1. Request"; `D2` method; `D3` URL; `D4` loại API; bảng Header (cột C=Field, H=Type,
I=Required, L=Example, AA=Description); bảng Body/Params (C=Field, H=Parent, I=Level, J=Type, N=Required, Q=Example,
AA=Description — sheet HD3/JOB dùng I=Type thay cho Level); bảng Response cùng cấu trúc; bảng CSV (C, H=Type,
I=Required, L=Example, AA); cuối sheet "Thông điệp mẫu" (`E..` = curl/JSON).

Header chung (mọi sheet, dòng 8–11): `X-Request-Id` UUID Y; `X-Client-Id` String Y (vd `tthc-hthue`, TMS `tms-hthue`);
`X-Client-Secret` String Y; `Content-Type` String Y `application/json` (riêng HD3 không có dòng Content-Type).
Envelope phản hồi chung: `traceId` UUID Y, `status` Integer Y, `code` String Y (`OK`), `errorCode` String Y (`0`),
`message` String Y (`Thành công`), `data` Object N.

---

## 1. Sheet `HD1. Yêu cầu dữ liệu hóa đơn mu` — Danh sách HĐ mua vào

| Mục | Giá trị | Ô |
|---|---|---|
| Method / URL | `POST {{APC-Internal}}/hthue/v1/mua-vao` | D2, D3 |
| Loại | **Async** | D4 |
| Body | `nmmst` String Y; `tthang` String Y `MM/YYYY` (>=); `dthang` String Y (<=); `lbttmsthue` Boolean Y "Loại bỏ HĐ có trạng thái MST **người bán** là 03, 05, 06"; `lbbhphap` Boolean Y "Loại bỏ HĐ bất hợp pháp" | C14–C18 |
| Response data | `jobId` UUID Y; `jobCode` String Y `hthue.mua-vao`; `tthai` Enum Y (TIEP_NHAN, DANG_XU_LY, HOAN_THANH, LOI); `mta` N; `tdkxuat` Datetime N; `url` String N "URL S3 (nếu số lượng hóa đơn ít <10k)"; `tsdong` Integer N "(nếu … <10k)" | C30–C36 |
| CSV | **không header** (A40), **27 cột** | C42–C68 |
| Mẫu | curl (E72) JSON có dấu phẩy thừa; response (E73) dùng `tracedId`, status 202 | E72, E73 |

CSV HD1 (thứ tự bắt buộc): 1 `nbmst` String Y · 2 `khmshdon` Byte Y · 3 `khhdon` String Y · 4 `shdon` Integer Y ·
5 `tdlap` Date Y · 6 `nky` Datetime N · 7 `tthai` Byte Y · 8 `nbten` String Y · 9 `tgtcthue` BigDecimal Y ·
10 `tgtthue` BigDecimal Y · 11 `pctkct_sotien` · 12 `pctkkknt_sotien` · 13 `pctcttc_sotien` · 14 `pctktpvdc_sotien` ·
15 `pct0_sotien` · 16 `pct5_sotien` · 17 `pct5_sothue` · 18 `pct8_sotien` · 19 `pct8_sothue` · 20 `pct10_sotien` ·
21 `pct10_sothue` · 22 `pctkhac_sotien` · 23 `pctkhac_sothue` (11–23 BigDecimal N) · 24 `bhphap` Byte N ·
25 `bhpldo` String N · 26 `bhpngay` Datetime N · 27 `ttmstnban` String Y.

Nghiệp vụ (từ prompt + `InvoicePeriodResolver` backfill, đã thống nhất): MST người mua = `NMMST`; kỳ mua vào =
tháng(`NKY` → `NCMA` → `TDLAP`) = cột `HD_MST_LIST.KY` với `CHIEU='MUA_VAO'`.

## 2. Sheet `HD3. Yêu cầu dữ liệu hóa đơn mu` — Chi tiết 1 hóa đơn

| Mục | Giá trị | Ô |
|---|---|---|
| Method / URL | `GET {{APC-Internal}}/hthue/v1/chi-tiet` | D2, D3 |
| Loại | **Sync** | D4 |
| Params | `nbmst` String Y; `khmshdon` **String** Y (vd 1); `khhdon` String Y (vd `CT26MAA`); `shdon` Integer Y (99); `tdlap` Date Y (2026-10-01) — STT 4 lặp ở A16/A17 | C13–C17 |
| Response data | `nbmst` String Y; `khmshdon` **Byte** Y; `khhdon` Y; `shdon` Integer Y; `tdlap` Date Y; `nky` Datetime N; `tthai` Byte Y; `nbten` Y; `tgtcthue`, `tgtthue` BigDecimal Y; `tsuat` String Y / `thtien` / `tthue` BigDecimal Y (Parent=`thttltsuat`, Level 2 — **không có dòng riêng cho `thttltsuat`**); `ttmstnban` String Y; `bhphap` Byte N; `bhpldo` N; `bhpngay` N; `dshhdvu` List<Object> N → `ten` String Y, `thtcthue` BigDecimal N, `ltsuat` String N, `tthue` BigDecimal N (Level 3) | C28–C49 |
| Mẫu | E54 curl GET query; E55 response: `thttltsuat` là **mảng** đối tượng, `tsuat` = `"8%"` (bảng ghi 0.08); status 202 | E54, E55 |

## 3. Sheet `HD5. Yêu cầu dữ liệu hóa đơn bá` — Danh sách HĐ bán ra

Như HD1, khác: body `nbmst` (MST người bán) (C14); `lbttmsthue` = loại HĐ có trạng thái MST **người mua** 03/05/06 (AA17);
`jobCode` `hthue.ban-ra` (Q31). CSV **không header, 29 cột** (C42–C70):
1 `nbmst` · 2 `khmshdon` · 3 `khhdon` · 4 `shdon` · 5 `tdlap` · 6 `nky` · 7 `tthai` · 8 `nmten` String N "Tên MST người mua (trên DKT)" ·
9 `nmmst` String N · 10 `tgtcthue` Y · 11 `tgtthue` Y · 12 `tgtttbso` BigDecimal Y · 13 `pctkct_sotien` · 14 `pctkkknt_sotien` ·
15 `pctcttc_sotien` · 16 `pctktpvdc_sotien` · 17 `pct0_sotien` · 18 `pct5_sotien` · 19 `pct5_sothue` · 20 `pct8_sotien` ·
21 `pct8_sothue` · 22 `pct10_sotien` · 23 `pct10_sothue` · 24 `pctkhac_sotien` · 25 `pctkhac_sothue` · 26 `bhphap` ·
27 `bhpldo` · 28 `bhpngay` · 29 `ttmstnmua` String **Y**.
Kỳ bán ra = tháng(`TDLAP`) = `HD_MST_LIST.KY` với `CHIEU='BAN_RA'`, MST = `NBMST`.

## 4. Sheet `HD7. Thông tin tổng hợp tờ khai` — Gợi ý số liệu tờ khai

| Mục | Giá trị | Ô |
|---|---|---|
| Method / URL | `POST {{APC-Internal}}/hthue/v1/goi-y` | D2, D3 |
| Loại | **Sync** | D4 |
| Body | `mst` String Y "MST cần hoàn"; `tthang` String Y; `dthang` String Y (không ghi định dạng; ví dụ `09/2026`) | C14–C16 |
| `data.mvao` Object Y "chỉ tiêu 23, 24" | `tgtmvao` BigDecimal Y; `tgttmvao` BigDecimal Y; `slmvao` Integer Y | C28–C31 |
| `data.bra` Object Y | `tgtbr0` (29), `tgtbr5` (30), `tgttbr5` (31), `tgtbr10` (32), `tgttbr10` (33), `tgtbrkct` (26), `tgtbrkkknt` (32a), `tgtbrktgtt` (32b "Không tính vào giá tính thuế GTGT"), `tgtbrktpvdc` (34a "Không thuộc phạm vi điều chỉnh") — tất cả BigDecimal **Y** | C32–C41 |
| Mẫu | E46 toàn số 0, status 202 | E45, E46 |

**Không có** chỉ tiêu cho 8% và nhóm thuế suất KHAC (trong khi CSV HD1/HD5 có `pct8_*`, `pctkhac_*`).

## 5. Sheet `HD8. Kiểm tra chi tiết hóa đơn ` (tên sheet có dấu cách cuối) — Kiểm tra danh sách HĐ mua vào

| Mục | Giá trị | Ô |
|---|---|---|
| Method / URL | `POST {{APC-Internal}}/hthue/v1/mua-vao/kiem-tra` | D2, D3 |
| Loại | **Async** | D4 |
| Body | `nmmst` String Y; `tngay` Date Y "Từ tdlap nhỏ nhất trong danh sách HĐ cần kiểm tra"; `dngay` Date Y "Đến tdlap lớn nhất…"; `s3url` String Y "Đường dẫn S3 lưu danh sách HĐ cần kiểm tra (định dạng CSV)" | C14–C17 |
| Response data | `jobId`, `jobCode` (vd **`hthue.ban-ra`**), `tthai` | C29–C31 |
| CSV kết quả | không header; "output gồm các HĐ bị lệch: không tồn tại, hoặc một trong các giá trị bị lệch" (A35); 27 cột cùng tên/thứ tự HD1 nhưng `tthai`…`pctkhac_sothue` Required **N**, `ttmstnban` Y | C37–C63 |
| Mẫu | E68 curl `tngay`/`dngay` dạng `yyyy-MM-dd`; E69 `tracedId`, `hthue.ban-ra` | E68, E69 |

**Không có** mô tả cấu trúc file CSV đầu vào, quy tắc so sánh từng trường, sai số tiền.

## 6. Sheet `TMS phản hồi danh sách đã hoàn` — Cập nhật HĐ đã hoàn thuế

| Mục | Giá trị | Ô |
|---|---|---|
| Method / URL | `POST {{APC-Internal}}/hthue/v1/cap-nhat-hoa-don-hoan-thue` | D2, D3 |
| Loại | **Sync** | D4 |
| Header | `X-Client-Id` vd `tms-hthue` (L9) — curl dùng `tthc-hthue` (E49) | L9, E49 |
| Body | `s3url` String Y "Đường dẫn S3 lưu danh sách HĐ cần kiểm tra" | C14 |
| Response | `status` vd **200** (Q21); `data.jobId`, `jobCode` `hthue.cap-nhat` (Q27), `tthai` `TIEP_NHAN` (Q28); mẫu E50 status 202, `hthue.ban-ra` | C20–C28, E50 |
| CSV **đầu vào** ("Cấu trúc file đầu CSV", A31) | không header (A32 chép nguyên văn mô tả file kết quả HD8); 12 cột: 1 `nbmst` Y · 2 `khmshdon` Byte Y · 3 `khhdon` Y · 4 `shdon` Integer Y · 5 `tdlap` Date Y · 6 `nky` Datetime N · 7 `tthai` Byte Y · 8 `nbten` Y · 9 `tgtcthue` Y · 10 `tgtthue` Y · 11 `thttltsuat` String N (JSON, vd `[{"tsuat":"8%","thtien":940000,"tthue":75200}]`) · 12 `ttmstnban` Y | C34–C45 |

Không có: MST người được hoàn (người mua), mã/số hồ sơ hoàn, quyết định hoàn, kỳ hoàn, số tiền hoàn, thời điểm hoàn.

## 7. Sheet `Kiểm tra và lấy kết quả job`

| Mục | Giá trị | Ô |
|---|---|---|
| Method / URL | `GET {{APC-Internal}}/hthue/v1/job/{{jobId}}` | D2, D3 |
| Loại | Sync | D4 |
| Header | có `Content-Type` Y (GET) | C11 |
| Variable | `jobId` String Y | C14 |
| Response data | `jobId` UUID Y; `jobCode` Y; `tthai` Enum Y (vd HOAN_THANH); `mta` N; `tdkxuat` Datetime N; `url` String N "URL S3"; `tsdong` Integer N "Tổng số dòng" (không điều kiện <10k) | C26–C32 |
| Mẫu | E36 curl GET kèm `--data` body; E37 `tracedId`, status 202, `tdkxuat` `"28/10/2026 9:52:19"`, dấu phẩy thừa | E36, E37 |

---

## 8. Đối chiếu nguồn dữ liệu Doris (`datahub_hddt`, DDL v4 — xác minh trên Doris DEV 192.168.1.77 ngày 2026-10-31, chỉ đọc)

| Bảng | Khóa / phân vùng | Dùng cho |
|---|---|---|
| `HOADON_HDR` (174 cột) | UNIQUE (`TDLAP`,`CQT`,`LOAI_HD`,`ID`); partition tháng `TDLAP`; hash `ID` 48; **không có index phụ** | HD1/HD3/HD5/HD8 trường header |
| `HOADON_DTL` (37 cột) | UNIQUE (`TDLAP`,`CQT`,`LOAI_HD`,`IDHDON`,`STT`); partition tháng `TDLAP`; hash `IDHDON` | HD3 `dshhdvu` |
| `HD_MST_LIST` (22 cột) | UNIQUE (`KY`,`MST`,`CHIEU`,`LOAI_HD`,`TDLAP`,`CQT`,`ID`); partition tháng `KY`; hash `MST` 48 | Tìm HĐ theo MST + kỳ (HD1, HD5, HD7 bán ra, HD8) |
| `HD_MST_SUMMARY` (9 cột) | AGGREGATE (`KY`,`MST`,`CHIEU`,`LOAI_HD`,`BUSINESS_ID`) SUM `SO_HOA_DON`,`TONG_THANH_TOAN`,`TONG_TRUOC_THUE`,`TONG_TIEN_THUE` | HD7 `mvao` (BUSINESS_ID=`HDDT_GROSS`; bỏ dòng marker `__BF_APPLY_MARKER__`/KY 1900-01-01 — KI-03 backfill) |
| `HD_MST_APPLIED_STATE`, `HD_MST_DELTA_JOURNAL` | ledger nội bộ của backfill/realtime | Không dùng trực tiếp |

Phạm vi dữ liệu trong Doris (quyết định C-17 backfill, 2026-10-30): **chỉ** HĐ `TTHAI ∈ {1,2,3,5}` và `TTXLY ∈ {5,6,8}`.
Kỳ `KY` = ngày đầu tháng. Quy tắc gom thuế suất (`Tsuat_inv_mtt.txt`): `kct`, `kkknt`, `0%`, `5%`, `8%` (chỉ INV), `10%`;
còn lại (kể cả 8% của MTT) → `PCTKHAC_*`; `thttltsuat` rỗng ⇒ toàn bộ `TGTCTHUE` vào `PCT10_SOTIEN`, thuế = /10.
Không có cột `PCTCTTC_SOTIEN`, `PCTKTPVDC_SOTIEN`, trạng thái MST. Nguồn trạng thái MST ứng viên: `doris.NNT_HIS`
(`TRANG_THAI`, bản ghi hiện hành `TD='2100-01-01'`, khóa `PARTNER,MST,MST_CU`) — theo DDL `Doris_HDDT_cấu truc bảng/NNT_*.txt`;
**không tồn tại trên Doris DEV**.

Chi tiết từng vấn đề: [`API_SPEC_ISSUES.md`](API_SPEC_ISSUES.md). Ma trận trường: [`API_TRACEABILITY.md`](API_TRACEABILITY.md).
