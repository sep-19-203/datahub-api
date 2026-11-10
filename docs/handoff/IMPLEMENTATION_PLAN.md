# Kế hoạch triển khai

| Phase | Nội dung | Trạng thái |
|---|---|---|
| 1 Spec Analysis | Đọc 7 sheet, phân tích, traceability, 40 vấn đề SI-xx | XONG (`docs/spec/`) |
| 2 Foundation | Module, worktree/branch, Spring Boot 3.5/Java 21, Doris DataSource + DSE CqlSession (kho job), S3, security, lỗi, logging, OpenAPI | XONG |
| 3 API | HD3 → HD7 → job engine + GET job → HD1 → HD5 → HD8 → TMS | XONG về mã; HD8 chốt 01/11; sổ hoàn TMS cài 01/11 (Doris HD_SO_HOAN); HD7 chốt 01/11 (THTTLTSUAT + DTL); SI-24 dùng NNT_HIS tạm |
| 4 Testing | Unit, contract, IT DSE/MinIO/Doris DEV (OUTFILE thật), HTTP E2E local | 97 unit PASS; IT Doris vòng 4 BLOCKED (BE DEV hết bộ nhớ); tải NOT_RUN |
| 5 Delivery | Dockerfile, OpenShift, README, deploy/sizing, handoff | XONG (mẫu, chưa áp dụng) |

## Việc tiếp theo (ưu tiên)
1. Lấy xác nhận nghiệp vụ cho SI-24 (nguồn trạng thái MST) → cấu hình `MST_STATUS_SOURCE_SQL`, thêm IT với bảng thật.
2. Chạy OUTFILE với S3 mà BE DEV tới được (KI-API-01).
3. Chốt hợp đồng với TTHC/TMS: SI-01..07, SI-14, SI-29, SI-36, SI-37.
4. HD7 đã chốt 01/11; còn chạy thật trên DEV + đo hiệu năng DTL (KI-API-23); thêm mã 32b/34a khi có. Sổ hoàn TMS: chạy IT/E2E Doris khi DEV hồi phục; bổ sung mã hồ sơ/QĐ khi TMS chốt.
5. Kiểm thử tải trên môi trường TEST; quyết định parallel OUTFILE, kích thước lô HD8, summary theo thuế suất.
