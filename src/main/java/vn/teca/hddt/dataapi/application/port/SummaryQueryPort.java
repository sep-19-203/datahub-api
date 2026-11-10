package vn.teca.hddt.dataapi.application.port;

import vn.teca.hddt.dataapi.domain.common.MonthRange;
import vn.teca.hddt.dataapi.domain.hd7.RateBreakdown;
import vn.teca.hddt.dataapi.domain.invoice.Direction;

/** Số liệu tổng hợp cho HD7. */
public interface SummaryQueryPort {
    /**
     * Tổng theo mã thuế suất của tập HĐ hợp lệ một chiều (HD_MST_LIST IS_ELIGIBLE=1, R1 nếu bật): THTTLTSUAT; HĐ thiếu
     * THTTLTSUAT lấy dòng hàng HOADON_DTL; HĐ không có thuế suất ở đâu cả suy từ tỷ lệ thuế/giá trị header (04/11).
     */
    RateBreakdown breakdown(Direction direction, String mst, MonthRange range);
}
