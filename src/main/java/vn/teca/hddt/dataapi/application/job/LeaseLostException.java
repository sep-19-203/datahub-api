package vn.teca.hddt.dataapi.application.job;

import java.util.UUID;

/** Worker không còn giữ job (lease hết hạn/bị nhận lại, hoặc pod dừng) ⇒ bỏ kết quả, không ghi trạng thái. */
public class LeaseLostException extends RuntimeException {
    public LeaseLostException(UUID jobId) {
        super("Mất quyền xử lý job " + jobId);
    }
}
