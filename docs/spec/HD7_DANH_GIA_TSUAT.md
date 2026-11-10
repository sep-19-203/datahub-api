# Đánh giá: logic `Tsuat_inv_mtt.txt` có đáp ứng HD7 (`data.bra`) không? — 2026-11-01

> **Cập nhật 06/11 (SI-51):** 13 trường `pct*_sotien/sothue` đã bị gạch khỏi đặc tả HD1/HD5/HD8 và đã bỏ khỏi mã. Nội dung về các cột này dưới đây chỉ còn giá trị lịch sử.

Nguồn: `GiaiPhapAPI/Tsuat_inv_mtt.txt` (logic ETL đổ `THTTLTSUAT` → `PCT*_SOTIEN/SOTHUE`, đang dùng ở backfill/realtime) và
HD7!C33–C41 của `API HDDT - TTHC - TMS.xlsx`; yêu cầu nghiệp vụ `Hoàn thuế GTGT.xlsx` Danh sách công việc!E20
("0%, 5%, 10%, 8% … chỉ tiêu 26 đến 32a").

## 1. Logic hiện có
| `tsuat` (trim, lowercase) | INV | MTT |
|---|---|---|
| `kct` | PCTKCT_SOTIEN | PCTKCT_SOTIEN |
| `kkknt` | PCTKKKNT_SOTIEN | PCTKKKNT_SOTIEN |
| `0%` | PCT0_SOTIEN | PCT0_SOTIEN |
| `5%` | PCT5_SOTIEN / PCT5_SOTHUE | như INV |
| `8%` | PCT8_SOTIEN / PCT8_SOTHUE | **không có nhánh ⇒ rơi vào KHAC** |
| `10%` | PCT10_SOTIEN / PCT10_SOTHUE | như INV |
| còn lại (mọi mã khác, kể cả rỗng = "NA") | cộng dồn PCTKHAC_SOTIEN / PCTKHAC_SOTHUE | như INV |
| `thttltsuat` rỗng | toàn bộ TGTCTHUE → PCT10_SOTIEN, thuế = TGTCTHUE / 10 | như INV |

## 2. Đối chiếu 9 chỉ tiêu HD7
| Trường HD7 | Chỉ tiêu | Nguồn từ logic | Đáp ứng |
|---|---|---|---|
| tgtbrkct | [26] | PCTKCT_SOTIEN | ✓ |
| tgtbr0 | [29] | PCT0_SOTIEN | ✓ |
| tgtbr5 / tgttbr5 | [30] / [31] | PCT5_SOTIEN / PCT5_SOTHUE | ✓ |
| tgtbr10 / tgttbr10 | [32] / [33] | PCT10_SOTIEN / PCT10_SOTHUE | ◐ — xem rủi ro 3 |
| tgtbrkkknt | [32a] | PCTKKKNT_SOTIEN | ✓ |
| tgtbrktgtt | [32b] "Không tính vào giá tính thuế GTGT" | **không có nhánh** — nếu nguồn có mã riêng thì đang lẫn trong KHAC | ✗ |
| tgtbrktpvdc | [34a] "Không thuộc phạm vi điều chỉnh" | **không có nhánh** — như trên | ✗ |
| (8%) | nghiệp vụ yêu cầu, hợp đồng không có trường | PCT8 (chỉ INV) | ✗ — chưa có chỗ trên HD7 |

## 3. Kết luận: KHÔNG đáp ứng đủ — đáp ứng 6/9 trường chắc chắn, 1 trường có rủi ro, 2 trường không có nguồn
1. **[32b], [34a]**: logic chỉ nhận `kct, kkknt, 0%, 5%, 8%, 10%`; mọi mã khác bị gộp vào KHAC cùng các thuế suất khác
   (vd "KHAC:3.5%") ⇒ từ cột PCT* **không tách được** [32b]/[34a].
2. **8%**: INV có PCT8 nhưng HD7 không có trường; **MTT đưa 8% vào KHAC** ⇒ nếu chốt "8% kê vào [32]/[33]" thì phần 8% của
   MTT sẽ bị thiếu (vì nằm lẫn trong KHAC) khi tính từ cột PCT*.
3. **Mặc định 10% khi `thttltsuat` rỗng**: thuế tính = TGTCTHUE/10 thay vì TGTTHUE thật ⇒ HĐ 8%/5% thiếu `thttltsuat` bị đếm vào
   [32]/[33] với số thuế sai (mẫu DEV ngày 30/10: `thttltsuat` rỗng ở 100% mẫu INV — DOCUMENT_ANALYSIS backfill).
4. KHAC cộng dồn nhiều mức ⇒ không dùng được cho bất kỳ chỉ tiêu riêng nào.

## 4. Đề xuất (cần nghiệp vụ chốt trước khi bật HD7)
- Tính `bra` trực tiếp từ `HOADON_HDR.THTTLTSUAT` (đã lưu nguyên mảng) theo **bảng mã `tsuat` → chỉ tiêu** cấu hình được,
  không dùng cột PCT* đã gộp; với HĐ không có `thttltsuat` dùng quy tắc riêng (vd lấy TGTCTHUE/TGTTHUE thật + thuế suất dòng
  hàng HOADON_DTL.LTSUAT).
- Cần chốt: (a) danh sách mã `tsuat` tương ứng [32b], [34a]; (b) 8% cộng vào chỉ tiêu nào ([32]/[33] hay trường mới);
  (c) quy tắc HĐ thiếu `thttltsuat`; (d) xử lý KHAC (mức thuế suất khác) trên tờ khai.
- Cho tới khi chốt: HD7 giữ trả 501 `HDDT-501-MAPPING` (không trả số 0/không bỏ 8%).

## 5. Chốt 01/11 và cài đặt
| Điểm | Chốt | Cài đặt |
|---|---|---|
| [32b], [34a] | Tạm trả **null** | `bra-tsuat.tgtbrktgtt/tgtbrktpvdc: []` ⇒ null (không phải 0) |
| 8% | Cộng vào [32]/[33], tiền thuế thật | `tgtbr10/tgttbr10: ["10%", "8%"]` |
| `thttltsuat` rỗng | Tính từ dòng hàng DTL | HĐ thiếu THTTLTSUAT ⇒ `HOADON_DTL` theo `LTSUAT` (bỏ dòng ghi chú TCHAT=4, chiết khấu TCHAT=3 trừ đi); HĐ chỉ 1 thuế suất lấy TGTCTHUE/TGTTHUE header; HĐ không có dòng nào có thuế suất ⇒ nhóm không xác định (cảnh báo) |
| KHAC | Bỏ qua, không chặn | `unassigned-tsuat-policy: IGNORE` + log WARN (mã, thành tiền, thuế) |

Nguồn số liệu: **không** dùng cột PCT* (đã gộp theo logic Tsuat) mà tách `THTTLTSUAT` trong SQL
(`explode_json_array_json` + `get_json_string`), mã chuẩn hóa ở `TaxRateCode` ("10.00%"/"10" ⇒ "10%"; chữ thường).
Giới hạn: số trong VARIANT/JSON là double ⇒ chính xác ~15–16 chữ số có nghĩa (VND thực tế nằm trong giới hạn).
Kiểm chứng: 104 unit PASS; `EXPLAIN` 3 câu SQL trên Doris DEV PASS (LIST 2/96 tablet; HDR, DTL nhận runtime filter); chạy thật
BLOCKED do BE .78 hết bộ nhớ (KI-API-20).

## 6. Cập nhật 04/11 theo GiaiPhap v3.8 (người dùng duyệt, `output/hd7_kiem_tra_va_doi_soat.md`)
| Điểm | Trước | Sau |
|---|---|---|
| R1 mẫu số (SI-54) | Không lọc | Mẫu 1, hoặc mẫu 5 có thuế ≠ 0 — cả mvao và bra (`hd7.form-rule-enabled`) |
| [23]/[24] (SI-55) | `HD_MST_SUMMARY` mọi thuế suất; `slmvao` = SO_HOA_DON | Theo mã thuế suất như bán ra, chỉ `0%, 5%, 10%` (`hd7.mvao-tsuat`); `slmvao` = số HĐ hợp lệ |
| 8% (SI-56) | Cộng [32]/[33] (chốt 01/11) | Không ánh xạ (GiaiPhap Q4, chốt 26/10) — bỏ qua + WARN |
| HĐ không có thuế suất ở đâu (SI-57) | "Không xác định", bỏ qua | Suy từ tỷ lệ TGTTHUE/TGTCTHUE header (10/8/5%) |
| HĐ 1 thuế suất header rỗng | Mất số | Lấy tổng dòng hàng |

Kiểm trên Doris DEV (code mới, chạy adapter trực tiếp): `034097009513` 07/2026 bra [32]/[33] = 1.279.793.657.157 / 127.979.366.223
(trước: 0); `1021650246` 05/2025 mvao 1.654.392.757 / 165.439.276 / 47 HĐ (trước: 2.194.392.745 / 208.639.275,04 / 61), bra [32] =
2.135.420.030 (+ 8% 539.999.988 bỏ qua) = Σ TGTCTHUE mẫu 1; `1055971031` 06/2025 `slmvao` 24 (trước 69). 122 unit PASS.

## 7. Cập nhật 05/11 — phần còn lệch GiaiPhap v3.8 (người dùng yêu cầu "cập nhật nốt")
| Quy tắc v3.8 | Trước | Sau | Cấu hình |
|---|---|---|---|
| Bảng 6 [34a] = KTPVDC (SI-58) | null | Cộng mã `ktpvdc` (0 nếu không có HĐ); [32b] không có trong v3.8 ⇒ vẫn null | `bra-tsuat.tgtbrktpvdc` |
| R3/R4 kỳ theo ngày lập, HĐ thay thế/điều chỉnh theo ngày lập HĐ gốc (SI-59) | Cột KY danh sách (mua vào NKY→NCMA→TDLAP) | `TDLHDGOC` nếu có HĐ gốc, còn lại `TDLAP`; cả hai chiều | `period-basis: NGAY_LAP` / `KY_DANH_SACH` |
| R7 ngoại tệ (SI-60) | Cộng nguyên tệ | × TGIA khi DVTTE ≠ VND và TGIA > 0 | `currency-conversion-enabled` |
| D3 HĐ điều chỉnh thiếu THTTLTSUAT (SI-61) | Lấy dòng hàng/header | Đóng góp 0 (TCHAT = 3); HĐ thay thế giữ nguyên | `adjustment-without-rates-zero` |

Chưa làm được trong API: R8 lọc 06 (hợp đồng HD7 không có tham số — KI-API-37); chuỗi HĐ gốc nhiều cấp (Q8, KI-API-36);
bảng tổng `ht_sum_tong`/`dm_chitieu` (kiến trúc tầng nạp). Mở rộng ngoài tài liệu, giữ có chủ đích: suy thuế suất từ header
(SI-57; v3.8 ghi KXD); HĐ 1 thuế suất lấy tổng header (chốt 01/11; v3.8 cộng dòng hàng).

Kiểm trên Doris DEV (`NGAY_LAP` so với `KY_DANH_SACH`): `1021631537` 05/2025 bán ra 8% +242.230.030.891 từ HĐ điều chỉnh lập
06/2025 (bỏ qua vì 8%), [32] 281.681.670,75 (+81.818); mua vào [23] 156.968.428,75 / 25 HĐ (cũ 106.886.610,75 / 22);
`5700100256-030` 09/2025 bán ra có HĐ thay thế lập 01/2026 (8%); `1055971031` 06/2025 mua vào 646.339.920 / 12 HĐ (cũ
1.196.340.508 / 24 — khác HD1, KI-API-35); `034097009513` 07/2026 và `1021650246` 05/2025 không đổi. Thời gian bán ra
0,25–1,1 s, MST 690.416 HĐ 2,5 s. 125 unit PASS.

## 8. Hoàn thiện 06/11 — đối chiếu lần cuối (sheet sửa 04/11 16:53 + GiaiPhap v3.8)
Sheet HD7 không đổi (không gạch/đỏ): request `mst, tthang, dthang`; `mvao` 3 trường, `bra` 9 trường, tất cả Required Y.
Sửa thêm: HĐ thiếu THTTLTSUAT có 1 thuế suất dòng hàng ⇒ **tổng dòng hàng** (v3.8 R6, SI-62; trước lấy header). DEV: các
case đối soát không đổi; chỉ MST có HĐ thử lỗi (`1055901406` 05/2025) đổi [30]. 126 unit PASS.

| Yêu cầu | Trạng thái |
|---|---|
| Hợp đồng sheet (tên trường, kiểu, cấu trúc) | Đáp ứng |
| R1, R2, R3/R4 (cấp 1), R5, R6, R7, D3, D5, Bảng 6, Q4 | Đáp ứng (cấu hình được) |
| [32b] Required Y | Trả `null` — không có trong v3.8, chưa có mã thuế suất (SI-09); trả 0 là số giả |
| `status` 202 trong ví dụ | Giữ 200 (API Sync — SI-02) |
| R3 chuỗi ≤ 5 cấp (Q8), R8 lọc 06, D2 MST cũ, Q4b | Chưa — chờ Q8 / ngoài hợp đồng / người dùng hoãn (KI-API-25) / chờ nghiệp vụ |
| Suy thuế suất từ header (SI-57) | Mở rộng ngoài tài liệu, người dùng duyệt 04/11 |
| Kỳ HD7 khác HD1 (KI-API-35) | Chờ quyết định áp R3/R4 cho HD1/HD5 |
