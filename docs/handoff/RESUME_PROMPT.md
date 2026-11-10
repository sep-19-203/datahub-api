# RESUME PROMPT (dán vào session Claude mới)

Bạn tiếp tục phát triển module `hddt-data-api-service` (Java 21 / Spring Boot 3.5.16, Doris 4.0.8, kho job DSE keyspace datahub_hddt, S3/MinIO).
Repo `http://192.168.1.69/hddt1/datahub_hddt/hddt-data-api-service` (nhánh `main`; clone về máy nếu chưa có). Trả lời bằng tiếng Việt.

1. Đọc `CLAUDE.md`, rồi `docs/handoff/SESSION_HANDOFF.md`, `session_state.json`,
   `docs/spec/API_SPEC_ISSUES.md`, `docs/handoff/TECHNICAL_DECISIONS.md`, `KNOWN_ISSUES.md`, `API_COVERAGE.md`.
2. `git status`, `git log --oneline -10`; đối chiếu với handoff (không tin mù checkpoint).
3. Chuẩn bị JDK 21 + Maven (SESSION_HANDOFF "Môi trường"), chạy `mvn -B test` — phải 104/104 PASS trước khi sửa.
4. Tiếp tục từ `EXACT_NEXT_TASK`. Giữ nguyên các quy tắc đã chốt (HD7 01/11, HD8, NNT_HIS tạm, sổ hoàn Doris `HD_SO_HOAN`) cho tới khi
   người dùng xác nhận bằng văn bản; mọi xác nhận mới ghi vào `API_SPEC_ISSUES.md` (đổi trạng thái) + TECHNICAL_DECISIONS.
5. Sau mỗi mốc đã kiểm chứng: cập nhật `SESSION_HANDOFF.md`, `session_state.json`, `TEST_RESULTS.md`; commit cục bộ
   (không push). Không ghi credential/MST thật; Doris DEV chỉ đọc; không kết nối production.

File đặc tả gốc: `/Volumes/WORK/TECAPRO/HDDT_2026/GiaiPhapAPI/API HDDT - TTHC - TMS.xlsx`
(SHA-256 `4942682d0feea668cc1731d7a7f8ddd82dcf81e0493c962737b24d8414d8b524`; nếu khác ⇒ đọc lại và cập nhật docs/spec).
