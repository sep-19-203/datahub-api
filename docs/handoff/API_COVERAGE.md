# Độ phủ API so với tiêu chí DONE (prompt mục XVI) — cập nhật 2026-10-31 vòng 2

① Controller+DTO ② Validation ③ Service ④ SQL/xử lý thật ⑤ Response đúng contract ⑥ Lỗi ⑦ Unit/contract ⑧ Tích hợp ⑨ Không mock ⑩ Tài liệu.

| API | ①–③ | ④ | ⑤ | ⑥ | ⑦ | ⑧ | ⑨ | ⑩ | Kết luận |
|---|---|---|---|---|---|---|---|---|---|
| GET job | ✓ | ✓ DSE LWT | ✓ | ✓ | ✓ | ✓ DseJobStoreIT + HTTP | ✓ | ✓ | **DONE** |
| HD1 | ✓ | ✓ OUTFILE thật | ✓ CSV 27 | ✓ | ✓ | ✓ HTTP E2E | ✓ | ✓ | **DONE (DEV)** — trạng thái MST từ NNT_HIS tạm (KI-API-02); 2 cột tạm rỗng (SI-08) |
| HD3 | ✓ | ✓ | ✓ | ✓ 404/409/503/504 | ✓ | ✓ | ✓ | ✓ | **DONE (DEV)** — như HD1 về trạng thái MST |
| HD5 | ✓ | ✓ OUTFILE thật | ✓ CSV 29 | ✓ | ✓ | ✓ IT + HTTP | ✓ | ✓ | **DONE (DEV)** — như HD1 |
| HD8 | ✓ | ✓ tra lô Doris | ✓ CSV 27, chỉ dòng lỗi, nguyên dòng vào | ✓ | ✓ | ✓ HTTP trên DEV (3 kịch bản) | ✓ | ✓ | **DONE (DEV)** — quy tắc chốt 01/11 |
| HD7 | ✓ | ✓ theo chốt 01/11 (THTTLTSUAT + DTL); EXPLAIN + `DorisDevIT` + HTTP DEV PASS (01/11, sau sửa điều kiện JSON null) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | **DONE (DEV)** — 32b/34a null; KI-API-24 |
| TMS | ✓ | ✓ kiểm tra + staging DSE + ghi sổ hoàn Doris HD_SO_HOAN | ✓ | ✓ | ✓ (6 test luồng sổ hoàn) | ✓ DSE IT + `DorisRefundLedgerIT` + HTTP E2E PASS (01/11) | ✓ | ✓ | **DONE (DEV)**; thiếu trường hồ sơ/QĐ (KI-API-05) |

"DONE (DEV)": chạy thật trên hạ tầng DEV; trước production cần dữ liệu NNT thật, kiểm thử tải, môi trường PROD.
