-- BẢNG TẠM NNT_HIS — cập nhật 2026-11-01 theo GiaiPhapAPI/Doris_map DKT.xlsx (sheet NNT_HIS, 13 cột: tên + kiểu Doris).
-- Lịch sử trạng thái NNT; bản ghi hiện hành TD = '2100-01-01' (điều kiện của các view NNT_CN/NNT_DN/NNT_HKD).
-- LOAI_NNT: 0900 = cá nhân (NNT_CN), 0300/0310 = hộ kinh doanh (NNT_HKD), còn lại = doanh nghiệp/tổ chức (NNT_DN).
-- Khóa: (PARTNER, MST, FD) = 3 cột đầu của sheet (Doris yêu cầu cột khóa đứng đầu). File mapping không ghi khóa/NULL ⇒
-- khóa NOT NULL, cột khác cho phép NULL. API đọc trạng thái MST hiện hành qua
--   hddt.mst-status.source-sql = SELECT MST, TRANG_THAI FROM doris.NNT_HIS WHERE TD = '2100-01-01'
-- Thay bảng cũ (01/11): đổi tên NNT_HIS cũ → NNT_HIS_OLD_20261101, tạo bảng này, nạp lại dữ liệu giả
-- (scripts/doris/seed_nnt_gia_lap.sql). Biến: ${NNT_DB} (vd doris), ${REPLICATION} (vd "replication_num" = "1", hoặc rỗng).
CREATE DATABASE IF NOT EXISTS ${NNT_DB};

CREATE TABLE IF NOT EXISTS ${NNT_DB}.`NNT_HIS` (
    `PARTNER` VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'Mã đối tượng NNT',
    `MST` VARCHAR(14) NOT NULL COMMENT 'Mã số thuế',
    `FD` DATETIME NOT NULL COMMENT 'Hiệu lực từ',
    `TD` DATETIME NOT NULL COMMENT 'Hiệu lực đến; 2100-01-01 = bản ghi hiện hành',
    `TRANG_THAI` VARCHAR(2) NULL COMMENT 'Trạng thái NNT',
    `LOAI_NNT` VARCHAR(4) NULL COMMENT 'Loại NNT',
    `MA_QLT` VARCHAR(5) NULL COMMENT 'Mã CQT quản lý',
    `TEN_CQT_QLY` VARCHAR(1000) NULL COMMENT 'Tên CQT quản lý',
    `TEN_NNT` VARCHAR(2000) NULL COMMENT 'Tên NNT',
    `DOC` VARIANT NULL COMMENT 'Json lưu thông tin chi tiết của NNT',
    `MST_CU` VARCHAR(14) NULL COMMENT 'Mã số thuế cũ',
    `YN_CNUT` VARCHAR(2) NULL COMMENT 'Có phải cá nhân ủy quyền không',
    `SYNC_DATE` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời gian lưu vào Doris'
) ENGINE=OLAP
UNIQUE KEY(`PARTNER`, `MST`, `FD`)
DISTRIBUTED BY HASH(`MST`) BUCKETS 8
PROPERTIES (
    ${REPLICATION}
    "enable_unique_key_merge_on_write" = "true",
    "compression" = "ZSTD"
);
