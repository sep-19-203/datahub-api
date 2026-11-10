package vn.teca.hddt.dataapi.infrastructure.doris;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vn.teca.hddt.dataapi.application.port.ExportPort.ExportSpec;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.csv.CsvLayout;
import vn.teca.hddt.dataapi.domain.csv.CsvLayouts;
import vn.teca.hddt.dataapi.domain.invoice.Direction;

/**
 * SQL kết xuất HD1/HD5. Đầu vào người dùng CHỈ đi qua tham số '?' (driver escape). Phần ghép chuỗi chỉ gồm tên bảng
 * đã kiểm, hằng số trong mã, SQL nguồn trạng thái MST (cấu hình tin cậy) và đường dẫn S3 sinh từ UUID job.
 * <ul>
 * <li>HD1 (MUA_VAO): HD_MST_LIST.KY = tháng(NKY→NCMA→TDLAP), MST = NMMST; trạng thái MST xét người BÁN (NBMST).</li>
 * <li>HD5 (BAN_RA): KY = tháng(TDLAP), MST = NBMST; trạng thái MST xét người MUA (NMMST).</li>
 * <li>lbttmsthue: loại HĐ có trạng thái thuộc danh sách cấu hình (mặc định 03, 05, 06); không có trạng thái ⇒ giữ.
 * Trạng thái đối tác (cột ttmstnban HD1 / ttmstnmua HD5) tra view NNT_ALL_STATUS theo MST, không có thì theo MST cũ
 * (SI-65, {@link MstStatusSql}); chỉ tra các MST đối tác của lần xuất (TD-API-39).</li>
 * <li>lbbhphap: loại HĐ có BHPHAP = 1 (SI-27).</li>
 * <li>GiaiPhap v3.8 R1: chỉ HĐ mẫu 1, hoặc mẫu 5 có tiền thuế ≠ 0 — HD1 {@code hddt.hd1.form-rule-enabled} (SI-46),
 * HD5 {@code hddt.hd5.form-rule-enabled} (SI-47).</li>
 * <li>DB-P1-03 (TD-API-34, {@code hddt.doris.list-serving}): đọc mọi cột từ HD_MST_LIST DDL v5 (alias l), không JOIN
 * HOADON_HDR, không cần truy vấn khoảng TDLAP — cùng điều kiện lọc, cùng biểu thức CSV.</li>
 * </ul>
 */
public final class ExportSqlBuilder {

    public static final class BuiltSql {
        private final String sql;
        private final List<Object> args;

        public BuiltSql(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }

        public String sql() {
            return sql;
        }

        public List<Object> args() {
            return args;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BuiltSql)) {
                return false;
            }
            BuiltSql other = (BuiltSql) o;
            return Objects.equals(sql, other.sql)
                    && Objects.equals(args, other.args);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(sql);
            result = 31 * result + Objects.hashCode(args);
            return result;
        }

        @Override
        public String toString() {
            return "BuiltSql[sql=" + sql
                    + ", args=" + args + "]";
        }
    }

    /** GiaiPhap v3.8 R1 (SI-46 HD1, SI-47 HD5): HĐ GTGT (mẫu 1), hoặc HĐ khác mẫu 5 có phát sinh tiền thuế. */
    static final String FORM_RULE_R1 = "(h.KHMSHDON = 1 OR (h.KHMSHDON = 5 AND IFNULL(h.TGTTHUE, 0) <> 0))";
    /** R1 trên cột HD_MST_LIST v5 (alias l). */
    static final String FORM_RULE_R1_LIST = FORM_RULE_R1.replace("h.", "l.");

    private final DorisTables t;
    private final HddtProperties.MstStatus status;
    private final boolean purchaseFormRule;
    private final boolean salesFormRule;
    private final boolean listServing;

    public ExportSqlBuilder(DorisTables t, HddtProperties.MstStatus status) {
        this(t, status, true, true);
    }

    /** Một cờ R1 cho cả hai chiều. */
    public ExportSqlBuilder(DorisTables t, HddtProperties.MstStatus status, boolean formRule) {
        this(t, status, formRule, formRule);
    }

    public ExportSqlBuilder(DorisTables t, HddtProperties.MstStatus status, boolean purchaseFormRule,
                            boolean salesFormRule) {
        this(t, status, purchaseFormRule, salesFormRule, false);
    }

    /** @param listServing true ⇒ chỉ đọc HD_MST_LIST DDL v5 (DB-P1-03) */
    public ExportSqlBuilder(DorisTables t, HddtProperties.MstStatus status, boolean purchaseFormRule,
                            boolean salesFormRule, boolean listServing) {
        this.t = t;
        this.status = status;
        this.purchaseFormRule = purchaseFormRule;
        this.salesFormRule = salesFormRule;
        this.listServing = listServing;
    }

    /** Mua vào cần truy vấn khoảng TDLAP trước chỉ khi còn JOIN HOADON_HDR (đọc LIST v5 thì không). */
    public boolean needsPurchaseBounds() {
        return !fromList(Direction.MUA_VAO);
    }

    /**
     * list-serving chỉ áp khi HD_MST_LIST v5 có đủ cột của bố cục. HD1 v3 cần THTTLTSUAT, GCHDGOC (chỉ ở HOADON_HDR) ⇒ HD1
     * vẫn LIST → HDR kể cả khi bật cờ, tới khi LIST có 2 cột này (SI-52, KI-API-32); HD5 từ 08/11 cũng có thttltsuat,
     * lddctthe (THTTLTSUAT, GCHDGOC) nên cũng đọc HDR (SI-63).
     */
    boolean fromList(Direction d) {
        return listServing && CsvLineSql.listCovers(layoutOf(d));
    }

    public static CsvLayout layoutOf(Direction d) {
        return d == Direction.MUA_VAO ? CsvLayouts.HD1_PURCHASE : CsvLayouts.HD5_SALES;
    }

    /** Khoảng TDLAP thực tế của các dòng HD_MST_LIST sẽ xuất (mua vào) — chỉ để cắt partition HOADON_HDR. */
    public static final class TdlapBounds {
        private final java.time.LocalDate min;
        private final java.time.LocalDate max;

        public TdlapBounds(java.time.LocalDate min, java.time.LocalDate max) {
            this.min = min;
            this.max = max;
        }

        public java.time.LocalDate min() {
            return min;
        }

        public java.time.LocalDate max() {
            return max;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TdlapBounds)) {
                return false;
            }
            TdlapBounds other = (TdlapBounds) o;
            return Objects.equals(min, other.min)
                    && Objects.equals(max, other.max);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(min);
            result = 31 * result + Objects.hashCode(max);
            return result;
        }

        @Override
        public String toString() {
            return "TdlapBounds[min=" + min
                    + ", max=" + max + "]";
        }
    }

    /** Truy vấn rẻ (1 bucket HD_MST_LIST) lấy MIN/MAX TDLAP của HĐ mua vào trong kỳ — cùng điều kiện với truy vấn xuất. */
    public BuiltSql purchaseTdlapBounds(ExportSpec spec) {
        return new BuiltSql("SELECT MIN(l.TDLAP) AS MIN_TDLAP, MAX(l.TDLAP) AS MAX_TDLAP FROM " + t.list() + " l"
                + " WHERE l.KY >= ? AND l.KY <= ? AND l.MST = ? AND l.CHIEU = '" + Direction.MUA_VAO.name()
                + "' AND l.IS_ELIGIBLE = 1", Jdk8Compat.listOf(spec.range().firstDay(), spec.range().lastPeriod(), spec.mst()));
    }

    /** SELECT một cột {@code csv_line}, chưa có mệnh đề OUTFILE. */
    public BuiltSql select(ExportSpec spec, int queryTimeoutSeconds, boolean parallelOutfile) {
        return select(spec, queryTimeoutSeconds, parallelOutfile, null);
    }

    /**
     * @param purchaseBounds (mua vào) khoảng TDLAP lấy từ {@link #purchaseTdlapBounds} để cắt partition HOADON_HDR —
     *                       kỳ mua vào theo NKY→NCMA→TDLAP nên không suy được TDLAP từ tháng; null ⇒ không cắt.
     */
    public BuiltSql select(ExportSpec spec, int queryTimeoutSeconds, boolean parallelOutfile, TdlapBounds purchaseBounds) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT /*+ SET_VAR(query_timeout = " + queryTimeoutSeconds + ", enable_parallel_outfile = "
                + parallelOutfile + ") */ " + CsvLineSql.lineExpression(layoutOf(spec.direction()), fromList(spec.direction()))
                + " AS csv_line"
                + fromWhere(spec, purchaseBounds, args);
        return new BuiltSql(sql, args);
    }


    public BuiltSql countUpTo(ExportSpec spec, long limit, int queryTimeoutSeconds, TdlapBounds purchaseBounds) {
        List<Object> args = new ArrayList<>();
        String inner = "SELECT 1 AS x" + fromWhere(spec, purchaseBounds, args) + " LIMIT ?";
        args.add(limit);
        return new BuiltSql("SELECT /*+ SET_VAR(query_timeout = " + queryTimeoutSeconds + ") */ COUNT(*) AS N FROM ("
                + inner + ") c", args);
    }

    private String fromWhere(ExportSpec spec, TdlapBounds purchaseBounds, List<Object> args) {
        if (fromList(spec.direction())) {
            return listFromWhere(spec, args);
        }
        String counterparty = spec.direction() == Direction.MUA_VAO ? "h.NBMST" : "h.NMMST";
        StringBuilder sql = new StringBuilder(" FROM ").append(t.list()).append(" l JOIN ").append(t.hdr()).append(" h ON ")
                .append(DorisTables.LIST_HDR_JOIN)
                .append(statusJoin(spec, counterparty, args))
                .append(" WHERE l.KY >= ? AND l.KY <= ? AND l.MST = ? AND l.CHIEU = '").append(spec.direction().name())
                .append("' AND l.IS_ELIGIBLE = 1");
        args.add(spec.range().firstDay());
        args.add(spec.range().lastPeriod());
        args.add(spec.mst());
        if (spec.direction() == Direction.BAN_RA) {
            // Kỳ bán ra = tháng TDLAP ⇒ cắt partition HOADON_HDR cùng khoảng.
            sql.append(" AND h.TDLAP >= ? AND h.TDLAP < ?");
            args.add(spec.range().firstDay());
            args.add(spec.range().endExclusive());
        } else if (purchaseBounds != null) {
            sql.append(" AND h.TDLAP >= ? AND h.TDLAP <= ?");
            args.add(purchaseBounds.min());
            args.add(purchaseBounds.max());
        }
        if (spec.excludeMstStatus()) {
            if (spec.excludedStatusCodes().isEmpty()) {
                throw new IllegalStateException("Danh sách mã trạng thái MST cần loại trống");
            }
            sql.append(" AND (s.TRANG_THAI IS NULL OR s.TRANG_THAI NOT IN (")
                    .append(DorisTables.placeholders(spec.excludedStatusCodes().size())).append("))");
            args.addAll(spec.excludedStatusCodes());
        }
        if (spec.excludeIllegal()) {
            sql.append(" AND (h.BHPHAP IS NULL OR h.BHPHAP <> 1)");
        }
        if (spec.direction() == Direction.MUA_VAO ? purchaseFormRule : salesFormRule) {
            sql.append(" AND ").append(FORM_RULE_R1);
        }
        return sql.toString();
    }

    /**
     * DB-P1-03: chỉ HD_MST_LIST v5 — partition theo KY, bucket theo MST (1 tablet/tháng), không đọc HOADON_HDR. Kỳ mua vào/bán
     * ra đã nằm trong KY nên không cần điều kiện TDLAP.
     */
    private String listFromWhere(ExportSpec spec, List<Object> args) {
        String counterparty = spec.direction() == Direction.MUA_VAO ? "l.NBMST" : "l.NMMST";
        StringBuilder sql = new StringBuilder(" FROM ").append(t.list()).append(" l")
                .append(statusJoin(spec, counterparty, args))
                .append(" WHERE l.KY >= ? AND l.KY <= ? AND l.MST = ? AND l.CHIEU = '").append(spec.direction().name())
                .append("' AND l.IS_ELIGIBLE = 1");
        args.add(spec.range().firstDay());
        args.add(spec.range().lastPeriod());
        args.add(spec.mst());
        if (spec.excludeMstStatus()) {
            if (spec.excludedStatusCodes().isEmpty()) {
                throw new IllegalStateException("Danh sách mã trạng thái MST cần loại trống");
            }
            sql.append(" AND (s.TRANG_THAI IS NULL OR s.TRANG_THAI NOT IN (")
                    .append(DorisTables.placeholders(spec.excludedStatusCodes().size())).append("))");
            args.addAll(spec.excludedStatusCodes());
        }
        if (spec.excludeIllegal()) {
            sql.append(" AND (l.BHPHAP IS NULL OR l.BHPHAP <> 1)");
        }
        if (spec.direction() == Direction.MUA_VAO ? purchaseFormRule : salesFormRule) {
            sql.append(" AND ").append(FORM_RULE_R1_LIST);
        }
        return sql.toString();
    }

    /**
     * LEFT JOIN trạng thái MST đối tác (SI-65: view NNT_ALL_STATUS, tra cả MST cũ). TD-API-39: chỉ tra các MST đối tác của
     * chính lần xuất — truy vấn con trên HD_MST_LIST cùng kỳ/MST/chiều (1 tablet/tháng) — nên Doris lọc nguồn NNT trước khi
     * gộp thay vì gộp cả bảng. Truy vấn con dùng ở 2 nhánh (MST, MST_CU) ⇒ tham số thêm 2 lần, đứng trước tham số WHERE.
     */
    private String statusJoin(ExportSpec spec, String counterparty, List<Object> args) {
        String partner = spec.direction() == Direction.MUA_VAO ? "NBMST" : "NMMST";
        String keys = "(SELECT lk." + partner + " FROM " + t.list() + " lk WHERE lk.KY >= ? AND lk.KY <= ? AND lk.MST = ?"
                + " AND lk.CHIEU = '" + spec.direction().name() + "' AND lk.IS_ELIGIBLE = 1)";
        for (int branch = 0; branch < 2; branch++) {
            args.add(spec.range().firstDay());
            args.add(spec.range().lastPeriod());
            args.add(spec.mst());
        }
        return " LEFT JOIN " + MstStatusSql.derivedTable(status, keys) + " s ON s.MST = " + counterparty;
    }

    /** Thêm OUTFILE S3 (Doris 4.0.8). Giá trị cấu hình S3 không được chứa nháy kép / backslash. */
    public static String outfileClause(String bucket, String prefix, HddtProperties.S3 s3, HddtProperties.Export export) {
        StringBuilder p = new StringBuilder();
        prop(p, "s3.endpoint", s3.effectiveDorisEndpoint());
        prop(p, "s3.region", s3.region());
        prop(p, "s3.access_key", s3.effectiveDorisAccessKey());
        prop(p, "s3.secret_key", s3.effectiveDorisSecretKey());
        prop(p, "use_path_style", String.valueOf(s3.pathStyle()));
        prop(p, "max_file_size", Math.max(5, export.maxFileBytes() / (1024 * 1024)) + "MB");
        prop(p, "success_file_name", export.successFileName());
        return " INTO OUTFILE \"s3://" + safe(bucket) + "/" + safe(prefix) + "\" FORMAT AS CSV PROPERTIES (" + p + ")";
    }

    private static void prop(StringBuilder p, String k, String v) {
        if (v == null || Jdk8Compat.isBlank(v)) {
            throw new IllegalStateException("Thiếu cấu hình OUTFILE: " + k);
        }
        if (p.length() != 0) {
            p.append(", ");
        }
        p.append('"').append(k).append("\" = \"").append(safe(v)).append('"');
    }

    private static String safe(String v) {
        if (v.indexOf('"') >= 0 || v.indexOf('\\') >= 0 || v.indexOf('\n') >= 0) {
            throw new IllegalStateException("Giá trị cấu hình S3 chứa ký tự không cho phép");
        }
        return v;
    }
}
