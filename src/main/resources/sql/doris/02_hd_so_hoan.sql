-- SỔ HOÀN THUẾ (2026-11-01, người dùng yêu cầu bổ sung — SI-15). Nhật ký N-N: một dòng = một hóa đơn được TMS ghi nhận
-- "đã hoàn" trong một lần cập nhật (MCV = mã công việc = jobId API TMS). Không xóa: hủy/thu hồi đổi TTHOAN = 'DA_HUY'
-- (theo mô hình log N-N DA_DUYET/DA_HUY của giải pháp v4.1 / spec v1.4 TMS2_DanhDau, TMS2_HuyDanhDau).
-- Tên cột theo quy ước QĐ 1233 / datahub_hddt. Trường hợp đồng API 31/10 CHƯA cung cấp (MHSO, SQDINH, NQDINH, TDCNHOAN)
-- để NULL — bổ sung khi TMS gửi; MSTHSO hiện SUY RA từ NMMST của hóa đơn trên Doris (NGUON_MSTHSO = 'HD_NMMST').
-- Không ghi đè HOADON_HDR / HD_MST_SUMMARY.
CREATE TABLE IF NOT EXISTS ${DB}.`HD_SO_HOAN` (
    `TDLAP` DATE NOT NULL COMMENT 'Ngày lập hóa đơn',
    `NBMST` VARCHAR(14) NOT NULL COMMENT 'MST người bán',
    `KHMSHDON` TINYINT NOT NULL COMMENT 'Ký hiệu mẫu số',
    `KHHDON` VARCHAR(11) NOT NULL COMMENT 'Ký hiệu hóa đơn',
    `SHDON` INT NOT NULL COMMENT 'Số hóa đơn',
    `MCV` VARCHAR(36) NOT NULL COMMENT 'Mã công việc ghi sổ = jobId API cập nhật hóa đơn hoàn thuế',
    `TTHOAN` VARCHAR(12) NOT NULL COMMENT 'DA_HOAN | DA_HUY',
    `LOAI_HD` VARCHAR(3) NULL COMMENT 'INV | MTT (hóa đơn khớp trên Doris)',
    `CQT` VARCHAR(4) NULL,
    `ID` VARCHAR(36) NULL COMMENT 'ID hóa đơn trên HOADON_HDR',
    `MSTHSO` VARCHAR(20) NULL COMMENT 'MST NNT được hoàn',
    `NGUON_MSTHSO` VARCHAR(12) NULL COMMENT 'HD_NMMST = suy từ NMMST hóa đơn; TMS = do TMS gửi',
    `MHSO` VARCHAR(50) NULL COMMENT 'Mã hồ sơ hoàn (chờ TMS bổ sung vào hợp đồng)',
    `SQDINH` VARCHAR(50) NULL COMMENT 'Số quyết định hoàn (chờ bổ sung)',
    `NQDINH` DATE NULL COMMENT 'Ngày quyết định hoàn (chờ bổ sung)',
    `TDCNHOAN` DECIMAL(21,6) NULL COMMENT 'Thuế được chấp nhận hoàn (chờ bổ sung)',
    `TGTCTHUE` DECIMAL(21,6) NULL COMMENT 'Tổng tiền chưa thuế theo file TMS',
    `TGTTHUE` DECIMAL(21,6) NULL COMMENT 'Tổng tiền thuế theo file TMS',
    `THTTLTSUAT` STRING NULL COMMENT 'Tổng hợp thuế suất (JSON) theo file TMS',
    `TTMSTNBAN` VARCHAR(20) NULL COMMENT 'Trạng thái MST người bán theo file TMS',
    `TTHAI` TINYINT NULL COMMENT 'Trạng thái hóa đơn theo file TMS',
    `NKY` DATETIME NULL COMMENT 'Ngày ký theo file TMS',
    `STT_DONG` BIGINT NULL COMMENT 'Số thứ tự dòng trong file',
    `NGUON_FILE` VARCHAR(1024) NULL COMMENT 's3url của lần cập nhật',
    `SHA_FILE` VARCHAR(64) NULL COMMENT 'SHA-256 nội dung file',
    `CLIENT_ID` VARCHAR(64) NULL,
    `TDGHI` DATETIME NULL COMMENT 'Thời điểm ghi sổ',
    `LDHUY` VARCHAR(20) NULL COMMENT 'Lý do hủy (HUY_QD | THU_HOI | NNT_TU_THU_HOI)',
    `TDHUY` DATETIME NULL COMMENT 'Thời điểm hủy'
) ENGINE=OLAP
UNIQUE KEY(`TDLAP`, `NBMST`, `KHMSHDON`, `KHHDON`, `SHDON`, `MCV`)
AUTO PARTITION BY RANGE (date_trunc(`TDLAP`, 'month')) ()
DISTRIBUTED BY HASH(`NBMST`) BUCKETS 16
PROPERTIES (
    ${REPLICATION}
    "enable_unique_key_merge_on_write" = "true",
    "compression" = "ZSTD"
);
