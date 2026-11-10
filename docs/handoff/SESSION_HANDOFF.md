# SESSION HANDOFF — hddt-data-api-service

> **09/11 (tối) — ESB y `HmacFilter` của tthc-sharing + BỎ X-Client-Id/X-Client-Secret (TD-API-40 sửa, SI-66, KI-API-40):**
> đã kiểm tthc-sharing (code + config server DEV): trục chỉ gắn `x-partner` + `x-hmac`, chữ ký đúng là đã xác thực. Người
> dùng chốt: bỏ hẳn X-Client-Id/X-Client-Secret (chỉ gọi nội bộ); không khóa ESB ⇒ không xác thực; quyền theo x-partner.
> Bên gọi = `x-partner` → `hddt.security.partner-clients` (TTHC → tthc-hthue, TMS → tms-hthue; giữ client id cũ nên job
> cũ vẫn đúng chủ). Có khóa (`ESB_HMAC_KEY` hoặc `tthc.hmac.key`) ⇒ x-partner + x-hmac đúng bắt buộc, thiếu X-Request-Id ⇒
> traceId tự sinh. Không khóa ⇒ WARN lúc khởi động; thiếu x-partner ⇒ `default-client` (`noi-bo`, đủ quyền; trống ⇒ 401).
> `secret-sha256`, `mode`, `CLIENT_*_SECRET_SHA256` đã bỏ; partner-clients/default-client sai tên client ⇒ app không
> khởi động. AUDIT log thêm `partner=`. Swagger: ô x-partner (+ x-hmac khi bật). `mvn -o test` (JDK 8) 175/175. Chưa
> deploy (.101 vẫn jar 13:58), chưa thử qua trục thật. Trục phải gửi `x-partner: TMS` cho API TMS (TTHC ⇒ 403).
>
> **09/11 — Cấu hình ESB lấy từ `hddt-tthc-sharing` (TD-API-40, KI-API-40):** ESB gọi vào kèm `x-partner` + `x-hmac` = hex(HMAC-SHA256(khóa, `yyyyMMdd.<partner>`)) như `HmacFilter` của tthc-sharing. Khóa CHỈ qua biến môi trường `ESB_HMAC_KEY` (người dùng chốt 09/11; yml không có mặc định, không đặt ⇒ tắt); có khóa ⇒ `ClientAuthenticationFilter` kiểm chữ ký (`EsbHmacVerifier`, ngày giờ VN, lệch ±5 phút quanh nửa đêm, `ESB_ALLOWED_PARTNERS` tùy chọn) trước X-Client-Id/Secret — vẫn bắt buộc như sheet "Cấu hình ESB"; không có khóa ⇒ như cũ. Swagger thêm 2 ô x-partner/x-hmac vào Authorize khi bật. Phần gọi RA ESB của tthc-sharing (`esbint`, `api_key`) không dùng. `mvn clean package` 167/167. .101 đã bật (10:37). Chưa có khóa/mã đối tác thật của ESB, chưa thử qua ESB. Đã thử đăng ký Eureka + config server như các service HĐĐT (09/11) rồi BỎ theo người dùng: module chạy độc lập, chỉ giữ cấu hình ESB.
>
> **08/11 (2) — Swagger UI tự sinh X-Request-Id (`adapter/web/SwaggerUiConfig`):** thay bean `indexPageTransformer` của springdoc, chèn `requestInterceptor` vào `swagger-initializer.js`: ô X-Request-Id để nguyên giá trị mẫu ⇒ mỗi lần Execute gửi UUID v4 mới (dự phòng `crypto.getRandomValues` khi chạy http://IP); nhập UUID khác ⇒ giữ nguyên. Chỉ ảnh hưởng Swagger UI, không đổi API/hợp đồng. `SwaggerUiConfigTest` chạy biến đổi thật của springdoc 1.8.0 trên webjar swagger-ui 5.11.8, `SwaggerUiWiringTest` kiểm bean thay đúng `indexPageTransformer` và `/swagger-ui/swagger-initializer.js` có interceptor; đã thử tay trên Swagger UI 5.11.8 thật (2 lần Execute ⇒ 2 UUID khác, curl hiện UUID đã gửi).
>
> **08/11 — HD1 + HD3 theo `API HDDT - TTHC - TMS_v3_20261106.xlsx` (Claude, máy Windows, nhánh `hoan_thue_api_java8` từ 8c62723, đã commit bf2ce9e):** HD1 CSV 14 → **15 cột** (bỏ `nky` gạch ngang; đưa lại `thttltsuat` JSON; thêm `lddctthe`) — SI-52; HD3 thêm `lddctthe` cuối `data`; nguồn đề xuất `HOADON_HDR.GCHDGOC` (SI-53, chờ xác nhận). HD8 giữ 14 cột (KI-API-31); list-serving: HD1 vẫn đọc HDR vì LIST v5 thiếu 2 cột (KI-API-32). HD3 tra header 2 bước theo khóa literal như HD8 (TD-API-36). `mvn test` 135/135 (JDK 8 Corretto 1.8.0_504, Maven 3.9.16 `-o`); chưa chạy IT/DEV.
>
> **07/11 — DB-P1-02 (TD-API-35), nhánh `feature/hd8-mot-truy-van-job` (từ 935df11), đã commit + push 07/11:** HD8 một truy vấn ứng viên HD_MST_LIST/job + tra theo khóa literal mỗi lô; ngưỡng `hddt.hd8.index-max-candidates-per-key` (20) chọn cách cũ cho file nhỏ/người mua lớn; `hddt.hd8.candidate-index=false` = cách cũ. `mvn test` 131/131. IT DEV chỉ đọc: `IT_DORIS_URL=… [IT_HD8_KEYS=110000] "$MVN" -B verify -Pit -Dit.test=Hd8CandidateIndexIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` (2.222 khóa: trùng, chọn theo lô; 122.222 khóa: trùng, 223 s → 79 s); `ListServingEquivalenceIT` 2/2 gồm đường chỉ mục trên HDR và LIST v5.
>
> **06/11 tối — DB-P1-03 (TD-API-34), cùng nhánh, đã commit 07/11 cùng SI-51, chưa push:** cờ `hddt.doris.list-serving` (mặc định false) cho HD1/HD5/HD8/TMS đọc thẳng HD_MST_LIST DDL v5, không JOIN HOADON_HDR. `mvn test` 123/123 (JDK 8). IT: `IT_LIST_DB=<db v5 có dữ liệu> IT_LIST_DB_DROP=true IT_DORIS_URL=… "$MVN" -B verify -Pit -Dit.test=ListServingEquivalenceIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` (DB do toolkit `DorisApplyIT -Dbackfill.it.ddl=v5 -Dbackfill.it.keep=true` để lại) ⇒ 2/2 trên DEV 06/11. Chỉ bật trên cụm LIST v5 + đối soát `LIST_CONTENT_VS_HDR` = 0.
>
> **06/11 — nhánh `feature/bo-truong-pct-thue-suat` (tạo từ `origin/uat` 63b0431, Java 8):** bỏ 13 trường `pct*_sotien/sothue` khỏi CSV HD1 (27→14 cột), HD5 (29→16), HD8 (27→14) và mọi mã đọc/ghi/so sánh liên quan (SI-51); `mvn test` 118/118 PASS (JDK 8). Đã commit 07/11, chưa push. Client TTHC phải cập nhật bố cục CSV.

Cập nhật: 2026-11-01 vòng 5 (Claude Opus). Repo `http://192.168.1.69/hddt1/datahub_hddt/hddt-data-api-service` nhánh `main` (tách từ `hddt-toolkit` 01/11, giữ lịch sử).
Push chỉ khi người dùng yêu cầu.

## Chốt của người dùng (31/10 vòng 2)
MinIO `http://192.168.1.69:9000` (credential trong `~/.config/hddt/data-api-local.env`, KHÔNG trong repo) · 2 cột
`pctcttc_sotien`/`pctktpvdc_sotien` tạm rỗng · Spring Boot 3.5.16 · trạng thái job: Doris nếu được, không thì DSE keyspace
`datahub_hddt` ⇒ đã thử Doris (không được) ⇒ **DSE** · chạy `java -jar` local, chưa OpenShift · SI-24: tạo NNT_HIS tạm từ view
NNT_CN/DN/HKD · HD8: file vào = CSV HD1, chỉ trả dòng không hợp lệ · chi tiết nghiệp vụ theo `Hoàn thuế GTGT.xlsx`.

## Trạng thái
- 08/11 tối (2) (Claude, chưa commit, sau `2c07cfa` HD7): **HD5 theo Google Sheet sửa 08/11 09:36** (SI-63): CSV 16 → **18 cột** —
  thêm `thttltsuat` (cột 12, JSON như HD1 v3/HD3; 13 cột pct* đã gạch là gộp vào cột này) và `lddctthe` (cột 18, ← `h.GCHDGOC`
  như HD1/HD3, SI-53). HD5 không còn dùng list-serving (KI-API-32). `mvn test` 147/147; `DorisDevIT` 5/5 trên Doris DEV + MinIO
  (JDBC_STREAM và OUTFILE kiểm 18 cột); mẫu thật `1021631537` 06/2025 ra đúng 18 cột, `thttltsuat` dạng
  `[{"tsuat":"10%","thtien":50000000,"tthue":5000000}]`. HTTP chưa chạy (cần restart app). Bản dự phòng: `git stash` "WIP 08/11".
- 08/11 tối (Claude, sau khi gộp lên `4781ae6`): commit riêng **HD7** (04–08/11): adapter hai chiều `RateBreakdown`, R1, [23]/[24]
  0/5/10%, 8% không ánh xạ, suy thuế suất từ header, [34a] = ktpvdc, kỳ theo ngày lập/HĐ gốc, R7 ngoại tệ, D3, tổng dòng hàng
  (`single-rate-source`), `data.lddctthe` = null. Số hiệu đánh lại để không trùng remote: SI-51…61 → SI-54…64, KI-API-31…36 →
  33…38, TD-API-34/35 → 37/38. **Phần HD5 (`lddctthe`, `thttltsuat` theo sheet 08/11 09:36 — 18 cột) tách khỏi commit này**, bản
  đầy đủ lưu ở `git stash` "WIP 08/11: HD7 + HD5 lddctthe"; HD5 hiện vẫn 16 cột như remote.
- 06/11 (Claude, chưa commit): HD7 đối chiếu lần cuối với sheet (sửa 04/11 16:53, HD7 không đổi) + v3.8 — thêm SI-62
  (`hddt.hd7.single-rate-source: LINES`, HĐ 1 thuế suất lấy tổng dòng hàng như `v_stg_ts`). 126 unit PASS; adapter trên DEV
  không đổi số các case đối soát. Bảng còn lại: `HD7_DANH_GIA_TSUAT.md` §8. Collection Postman 7 API:
  `output/postman_hoan_thue_local.json` (38/38 PASS 06/11, chưa chạy TMS).
- 05/11 (Claude, nhánh `hoan_thue_api_java8`, chưa commit — code HD7 04/11 từng bị GitHub Desktop cất vào stash, người dùng đã
  khôi phục): **HD7 cập nhật nốt phần lệch GiaiPhap v3.8** — [34a] = `ktpvdc` (SI-58); kỳ theo ngày lập / ngày lập HĐ gốc cho
  cả hai chiều (SI-59, `hddt.hd7.period-basis`, env `HD7_PERIOD_BASIS`); quy đổi ngoại tệ R7 (SI-60); HĐ điều chỉnh thiếu
  THTTLTSUAT đóng góp 0 (SI-61). TD-API-38; mở KI-API-35 (HD1 vẫn kỳ NKY ⇒ lệch HD7), KI-API-36 (chuỗi gốc 1 cấp), KI-API-37
  (R8 ngoài hợp đồng). 125 unit PASS; chạy adapter trên Doris DEV hai chế độ kỳ (HD7_DANH_GIA_TSUAT §7). HTTP chưa chạy lại.
- 04/11 chiều (Claude, nhánh `hoan_thue_api_java8`, sau commit `f6ee7ca`): **HD7 cập nhật theo GiaiPhap v3.8** (người dùng duyệt
  "update all" trên `output/hd7_kiem_tra_va_doi_soat.md`): R1 cho mvao + bra (SI-54); [23]/[24] chỉ 0/5/10% tính từ chi tiết, bỏ
  `HD_MST_SUMMARY` (SI-55); 8% không ánh xạ (SI-56 — chờ nghiệp vụ xác nhận, KI-API-33); suy thuế suất từ header cho HĐ không có thuế
  suất ở đâu (SI-57, KI-API-24); HĐ 1 thuế suất header rỗng ⇒ tổng dòng hàng. TD-API-37. 122 unit PASS; chạy adapter mới trên Doris
  DEV khớp tổng header (HD7_DANH_GIA_TSUAT §6). HTTP HD7 chưa chạy lại với code mới (app local cần restart). Chưa commit.
- 04/11: đối soát HD5 job `a3c4fbb8` (5073 dòng) với DSE/MinIO/Doris DEV khớp 100% (`output/doi_soat_hd5_job_a3c4fbb8.md`).
- 04/11 (Claude, máy Windows, nhánh `hoan_thue_api_java8`): **HD5 theo Google Sheet `API HDDT - TTHC - TMS` sửa 01/11** + tài liệu
  giải pháp v3.8/slide v3.1: R1 mẫu số cho HD5 (SI-47, `hddt.hd5.form-rule-enabled`), trả ngay < 10k dòng như HD1
  (SI-06, `hddt.hd5.realtime-*`). Cấu hình chung `HddtProperties.ListExportSettings` (Hd1/Hd5). CSV giữ **29 cột**: dòng
  `thttltsuat` trên sheet bị gạch ngang = bỏ (người dùng xác nhận, SI-48). 16 cột tô đỏ chưa rõ nghĩa (SI-49, KI-API-30). `pctkhac_sotien`/`pctkhac_sothue`: mô tả sheet bị đảo (người dùng xác nhận) ⇒ map theo tên cột, đúng backfill (SI-50, cả HD1/HD5/HD8).
  118 unit/contract PASS (JDK 8 Corretto 1.8.0_512, Maven của IntelliJ, `-o`). IT/HTTP DEV chưa chạy: thiếu credential (KI-API-28).
  Mở thêm KI-API-29 (03/05/06 hiện hành vs 06 tại ngày lập). Chưa commit.
- 02/11 (Claude, đối chiếu HD1 với `GiaiPhap_HoanThue_DuLieuHD_Doris_v3.8.docx`, `API_DataHub_HDDT_HoanThue_DuLieuHD_v2.7.drawio`,
  `TrinhBay_HoanThue_DuLieuHD_v2.6.pptx`): người dùng chốt — giữ kỳ mua vào NKY→NCMA→TDLAP (SI-42), **áp quy tắc mẫu số R1
  cho HD1** (SI-46, TD-API-33), MST cũ để sau (KI-API-25). Cài **HD1 trả ngay < 10k dòng** (SI-06, TD-API-32,
  `RealtimeResultWaiter`, cấu hình `hddt.hd1.*`). 113 unit/contract PASS (JDK 21, Maven 3.9.16). **Chưa chạy IT/HTTP trên
  DEV**: máy chạy không tới được Doris 192.168.1.77:9030 (KI-API-27). Chưa commit.
- 93 unit/contract + 14 IT PASS (DSE DEV, MinIO 192.168.1.69, Doris DEV — gồm OUTFILE thật). HTTP end-to-end local PASS
  (HD1/HD5 OUTFILE, HD1→HD8 0 lỗi / sửa → 2 lỗi, HD3, HD7 501, TMS LOI do SI-15). Chi tiết `TEST_RESULTS.md`.
- 01/11 vòng 4: **sổ hoàn TMS đã cài** — Doris `datahub_hddt.HD_SO_HOAN` (đã tạo trên DEV, rỗng) + staging DSE (`cql/V2`, app tự
  ALTER khi `DSE_CREATE_SCHEMA=true`); thiết kế TD-API-27/28, SI-15. 97 unit PASS, DseJobStoreIT 5/5 PASS. **IT Doris sổ hoàn +
  HTTP E2E TMS chưa kiểm chứng**: BE 192.168.1.78 hết bộ nhớ (KI-API-20) — DDL + INSERT WITH LABEL chạy được, SELECT lỗi.
- 01/11 vòng 4: đánh giá `Tsuat_inv_mtt.txt` cho HD7 → **không đáp ứng đủ** (thiếu 32b/34a, 8% không có chỗ + MTT 8% vào KHAC,
  mặc định 10% khi thttltsuat rỗng) — `docs/spec/HD7_DANH_GIA_TSUAT.md`. HD7 vẫn 501.
- 01/11 vòng 5: **HD7 chốt và cài đặt** — 32b/34a trả null; 8% cộng [32]/[33] (thuế thật); HĐ thiếu THTTLTSUAT tính từ dòng hàng
  DTL; KHAC bỏ qua + WARN. Bán ra tách THTTLTSUAT trong SQL (không dùng PCT*), cấu hình `hddt.hd7.bra-tsuat` (TD-API-29).
  104 unit PASS; EXPLAIN DEV PASS; chạy thật BLOCKED (BE .78).
- 01/11 (sau): Doris DEV BE .78/.77 hết bộ nhớ ⇒ restart qua SSH root bằng khóa (máy này → .77/.78; .79 chưa có khóa).
  Sau restart: 105 unit + 15 IT PASS; E2E TMS → sổ hoàn PASS (đã dọn dòng thử); HD7 HTTP phát hiện + sửa lỗi THTTLTSUAT là
  JSON null bị bỏ sót (TEST_RESULTS 4f).
- Còn: sổ hoàn thiếu mã hồ sơ/QĐ/số tiền (KI-API-05); 32b/34a chờ mã tsuat; HĐ không thuế suất + không dòng hàng (KI-API-24);
  chờ dữ liệu NNT thật; đề nghị quản trị DEV đặt THP=madvise (KI-API-20).
- 01/11: quy tắc HD8 chốt — khóa mẫu số + ký hiệu + số HĐ + MST người bán; so tgtcthue, tgtthue (sai số 0); file ra = nguyên dòng đầu vào
  của dòng không hợp lệ. 94 unit PASS; HTTP trên DEV: HD1 nguyên vẹn → 0 lỗi, đổi tên/tthai → 0 lỗi, lệch tiền + HĐ không tồn tại → 2 dòng = đúng dòng vào.
- Hạ tầng DEV đã tạo: Doris `doris.NNT_HIS` + `doris.NNT_HKD` (theo `Doris_map DKT.xlsx`, 01/11; 8 + 2 dòng giả; bảng cũ giữ ở
  `doris.NNT_HIS_OLD_20261101`, xóa được), `datahub_hddt.HD_SO_HOAN` (rỗng); Doris **local** (127.0.0.1:9030, colima
  `/Volumes/WORK/doris-lab`) có cùng `doris.NNT_HIS`/`NNT_HKD`; DSE keyspace `datahub_hddt` (RF DC1:2) + bảng job; MinIO bucket `hddt`
  (có sẵn) — prefix `export/query-result/`, `export/hd8-result/` chứa vài kết quả thử (janitor dọn sau 7 ngày).

## Môi trường
- JDK 21: Temurin tải về scratchpad (`api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse`) hoặc
  `brew install openjdk@21`. Maven: `/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`.
- `~/.config/hddt/dse-dev.env` (toolkit, DSE DEV) + `~/.config/hddt/data-api-local.env` (file này nạp dse-dev.env; Doris DEV,
  MinIO, secret client giả). Mẫu: `config/data-api-local.env.example`.

## NEXT_COMMANDS
```bash
git clone http://192.168.1.69/hddt1/datahub_hddt/hddt-data-api-service.git && cd hddt-data-api-service   # hoặc cd vào bản clone sẵn có
export JAVA_HOME=<jdk21>; MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
"$MVN" -B test
( . ~/.config/hddt/data-api-local.env
  export IT_DSE_CONTACT_POINTS="$DSE_CONTACT_POINTS" IT_DSE_LOCAL_DC="$DSE_LOCAL_DC" IT_DSE_USERNAME="$DSE_USERNAME" \
         IT_DSE_PASSWORD="$DSE_PASSWORD" IT_S3_ENDPOINT=$S3_ENDPOINT IT_S3_ACCESS_KEY=$S3_ACCESS_KEY \
         IT_S3_SECRET_KEY=$S3_SECRET_KEY IT_DORIS_OUTFILE_S3_ENDPOINT=$S3_DORIS_ENDPOINT \
         IT_DORIS_URL='jdbc:mysql://192.168.1.77:9030/?useSSL=false'
  "$MVN" -B verify -Pit )
# khi Doris DEV hồi phục — chỉ IT sổ hoàn:
IT_DORIS_URL='jdbc:mysql://192.168.1.77:9030/?useSSL=false&connectTimeout=5000' "$MVN" -B verify -Pit -Dit.test=DorisRefundLedgerIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false
"$MVN" -B package -DskipTests && ./scripts/run-local.sh      # http://localhost:18080, Swagger /swagger-ui.html
```

## EXACT_NEXT_TASK
1. (XONG 01/11) IT Doris + E2E TMS + HD7 HTTP trên DEV — TEST_RESULTS 4f.
2. Đo hiệu năng HD7 nhánh DTL khi DEV có nhiều HĐ (KI-API-23); thêm mã 32b/34a vào `bra-tsuat` khi nghiệp vụ có; quyết định KI-API-24.
3. TMS bổ sung mã hồ sơ/số-ngày QĐ/số tiền hoàn + API hủy (DA_HUY) — KI-API-05.
4. Nạp NNT_HIS/NNT_HKD thật (hoặc trỏ `MST_STATUS_SOURCE_SQL`), chạy lại HD1/HD5 với `lbttmsthue=true`. Áp dụng DDL:
   `01_nnt_his_tam.sql` → `scripts/doris/seed_nnt_gia_lap.sql` (chỉ môi trường thử) → `03_nnt_hkd_tam.sql` (biến `${NNT_DB}`, `${REPLICATION}`).
5. Kiểm thử tải; PROD: DSE RF ≥ 3, OpenShift khi cần.

## Không được làm
Không sửa repo `hddt-toolkit` (backfill/realtime) từ đây; không ghi credential vào repo; không kết nối production; không thêm ghi thường (không LWT) vào `api_job`; không sửa/dọn dòng `HD_SO_HOAN` ngoài dòng thử do chính mình tạo.
