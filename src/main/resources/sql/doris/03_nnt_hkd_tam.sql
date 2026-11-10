-- BẢNG TẠM NNT_HKD — 2026-11-01 theo GiaiPhapAPI/Doris_map DKT.xlsx (sheet NNT_HKD, 20 cột: tên + kiểu Doris).
-- Bên PROD NNT_HKD là materialized view trên NNT_HIS (GiaiPhapAPI/Doris_HDDT_cấu truc bảng/NNT_HKD.txt):
-- bản ghi hiện hành (TD = '2100-01-01') có LOAI_NNT IN ('0300','0310'), các cột chi tiết tách từ DOC bằng JSON_EXTRACT_STRING.
-- Bảng tạm giữ đúng thứ tự cột và khóa DUPLICATE (PARTNER, MST, MST_CU) của view đó; làm mới toàn bộ bằng
-- INSERT OVERWRITE bên dưới (thay thế nguyên tử, tương đương refresh của MV). Biến: ${NNT_DB}, ${REPLICATION}.
-- Ghi chú file mapping: D9 của CQT_QLY ghi "Hiệu lực từ" — theo view PROD đây là $.CQT_QLY (CQT quản lý).
CREATE TABLE IF NOT EXISTS ${NNT_DB}.`NNT_HKD` (
    `PARTNER` VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'NNT',
    `MST` VARCHAR(14) NOT NULL COMMENT 'Mã số thuế; join NNT.MST = NNT_*.MST',
    `MST_CU` VARCHAR(14) NULL COMMENT 'MST cũ',
    `TEN_NNT` VARCHAR(2000) NULL COMMENT 'Tên NNT',
    `TRANG_THAI` VARCHAR(2) NULL COMMENT 'Trạng thái MST',
    `LOAI_NNT` VARCHAR(4) NULL COMMENT 'Loại NNT (0300, 0310)',
    `YN_CNUT` VARCHAR(2) NULL COMMENT 'Có phải cá nhân ủy quyền không',
    `MA_QLT` VARCHAR(5) NULL COMMENT 'CQT quản lý mã 5 số',
    `TEN_CQT_QLY` VARCHAR(1000) NULL COMMENT 'Tên CQT quản lý',
    `CQT_QLY` TEXT NULL COMMENT 'CQT quản lý (DOC.CQT_QLY)',
    `CQT_QTOAN` TEXT NULL COMMENT 'CQT quyết toán',
    `CAN_BO` TEXT NULL COMMENT 'Mã cán bộ',
    `PHONG_BAN` TEXT NULL COMMENT 'Phòng ban',
    `NNKD_CHINH` TEXT NULL COMMENT 'Ngành nghề kinh doanh chính',
    `LHINH_KTE` TEXT NULL COMMENT 'Mã loại hình kinh tế',
    `LHINH_KTE_CTIET` TEXT NULL COMMENT 'Mã loại hình kinh tế chi tiết',
    `KVKT` TEXT NULL COMMENT 'Khu vực kinh tế',
    `DN_LIEN_THONG` TEXT NULL COMMENT 'Doanh nghiệp liên thông',
    `HTHUC_HTOAN` TEXT NULL COMMENT 'Hình thức hạch toán: X độc lập, NULL phụ thuộc',
    `PPHAP_TTHUE` TEXT NULL COMMENT 'Phương pháp tính thuế: 0 không nộp GTGT, 1 khấu trừ, 2 trực tiếp GTGT, 3 trực tiếp doanh số, 4 khoán, 5 DT-CP (TNDN), 6 kê khai'
) ENGINE=OLAP
DUPLICATE KEY(`PARTNER`, `MST`, `MST_CU`)
DISTRIBUTED BY HASH(`MST`) BUCKETS 8
PROPERTIES (
    ${REPLICATION}
    "compression" = "ZSTD"
);

INSERT OVERWRITE TABLE ${NNT_DB}.`NNT_HKD`
SELECT H.`PARTNER`, H.`MST`, H.`MST_CU`, H.`TEN_NNT`, H.`TRANG_THAI`, H.`LOAI_NNT`, H.`YN_CNUT`, H.`MA_QLT`, H.`TEN_CQT_QLY`,
       JSON_EXTRACT_STRING(H.`DOC`, '$.CQT_QLY'), JSON_EXTRACT_STRING(H.`DOC`, '$.CQT_QTOAN'),
       JSON_EXTRACT_STRING(H.`DOC`, '$.CAN_BO'), JSON_EXTRACT_STRING(H.`DOC`, '$.PHONG_BAN'),
       JSON_EXTRACT_STRING(H.`DOC`, '$.NNKD_CHINH'), JSON_EXTRACT_STRING(H.`DOC`, '$.LHINH_KTE'),
       JSON_EXTRACT_STRING(H.`DOC`, '$.LHINH_KTE_CTIET'), JSON_EXTRACT_STRING(H.`DOC`, '$.KVKT'),
       JSON_EXTRACT_STRING(H.`DOC`, '$.DN_LIEN_THONG'), JSON_EXTRACT_STRING(H.`DOC`, '$.HTHUC_HTOAN'),
       JSON_EXTRACT_STRING(H.`DOC`, '$.PPHAP_TTHUE')
FROM ${NNT_DB}.`NNT_HIS` H
WHERE H.`TD` = '2100-01-01' AND H.`LOAI_NNT` IN ('0300', '0310');
