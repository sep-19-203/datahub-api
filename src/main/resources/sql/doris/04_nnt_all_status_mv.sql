-- VIEW TRẠNG THÁI MST NNT_ALL_STATUS (SI-65) — nguồn mặc định của hddt.mst-status.source-sql:
--     SELECT MST, MST_CU, TRANG_THAI FROM doris.NNT_ALL_STATUS
-- Đúng định nghĩa đang có trên Doris DEV 192.168.1.77 (đọc bằng SHOW CREATE MATERIALIZED VIEW, 08/11/2026): async MV làm mới
-- hằng ngày từ bản ghi HIỆN HÀNH (TD = '2100-01-01') của NNT_HIS — gồm cả DN lẫn HKD/cá nhân, KHÔNG có cột PARTNER nên một MST
-- có thể ra nhiều dòng (API lấy MAX). MST_CU = MST cũ của NNT: HĐ lập bằng MST cũ được tra trạng thái của NNT mang MST mới.
-- Module KHÔNG tự tạo view này — chỉ dùng khi môi trường (UAT/lab) chưa có. Biến: ${NNT_DB} (DEV: doris), giờ chạy đầu tiên.
CREATE MATERIALIZED VIEW IF NOT EXISTS ${NNT_DB}.NNT_ALL_STATUS
    (MST, MST_CU, TEN_NNT, TRANG_THAI, LOAI_NNT, MA_QLT, TEN_CQT_QLY)
BUILD IMMEDIATE REFRESH AUTO ON SCHEDULE EVERY 1 DAY STARTS "2026-11-09 05:00:00"
DUPLICATE KEY(`MST`)
DISTRIBUTED BY HASH(`MST`) BUCKETS AUTO
AS SELECT H.MST, H.MST_CU, H.TEN_NNT, H.TRANG_THAI, H.LOAI_NNT, H.MA_QLT, H.TEN_CQT_QLY
     FROM ${NNT_DB}.NNT_HIS H
    WHERE H.TD = '2100-01-01';

-- Làm mới ngay (sau khi nạp lại NNT_HIS):  REFRESH MATERIALIZED VIEW ${NNT_DB}.NNT_ALL_STATUS COMPLETE;
-- Kiểm tình trạng:                          SELECT Name, State, RefreshState FROM mv_infos('database' = '${NNT_DB}');
