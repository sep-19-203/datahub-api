-- 2026-11-01: staging cho ghi sổ hoàn (refund_item mang đủ dữ liệu dòng sổ) + bộ đếm hoàn trùng.
-- ALTER không có IF NOT EXISTS trên DSE 6.8 ⇒ installer bỏ qua lỗi "đã tồn tại" (chạy lại an toàn).
ALTER TABLE ${KS}.refund_item ADD (doris_cqt text, nmmst text, tgtcthue decimal, tgtthue decimal, thttltsuat text, ttmstnban text, tthai int, nky timestamp);
ALTER TABLE ${KS}.refund_batch ADD already_refunded_count bigint;
