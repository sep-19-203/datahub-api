package vn.teca.hddt.dataapi.domain.job;

/**
 * Trạng thái nội bộ (chi tiết hơn enum công khai). RETRY_WAIT vẫn hiển thị DANG_XU_LY: client không thấy lần thử lại.
 */
public enum JobState {
    QUEUED, RUNNING, RETRY_WAIT, SUCCEEDED, FAILED;

    public PublicJobStatus publicStatus() {
        switch (this) {
            case QUEUED:
                return PublicJobStatus.TIEP_NHAN;
            case RUNNING:
            case RETRY_WAIT:
                return PublicJobStatus.DANG_XU_LY;
            case SUCCEEDED:
                return PublicJobStatus.HOAN_THANH;
            case FAILED:
                return PublicJobStatus.LOI;
            default:
                throw new IllegalStateException("JobState không xác định: " + this);
        }
    }

    public boolean isActive() {
        return this == QUEUED || this == RUNNING || this == RETRY_WAIT;
    }
}
