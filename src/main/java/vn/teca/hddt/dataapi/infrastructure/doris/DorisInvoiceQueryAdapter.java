package vn.teca.hddt.dataapi.infrastructure.doris;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import vn.teca.hddt.dataapi.application.port.InvoiceQueryPort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.invoice.GoodsLine;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceHeader;
import vn.teca.hddt.dataapi.domain.invoice.TaxRateLine;

/**
 * HD3 trên Doris. Tìm qua HD_MST_LIST (BAN_RA: KY = tháng TDLAP, MST = NBMST ⇒ cắt partition + bucket theo MST) lấy khóa
 * UNIQUE (TDLAP, CQT, LOAI_HD, ID), rồi đọc HOADON_HDR theo đúng giá trị khóa đó (TD-API-36) — tránh quét toàn bộ HĐ của
 * một ngày/tháng (HDR không có index NBMST).
 * Dòng hàng hóa: một truy vấn theo khóa header, sắp theo STT.
 */
@Component
public class DorisInvoiceQueryAdapter implements InvoiceQueryPort {
    private static final Logger log = LoggerFactory.getLogger(DorisInvoiceQueryAdapter.class);
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private final JdbcTemplate jdbc;
    private final DorisTables t;

    public DorisInvoiceQueryAdapter(@Qualifier("dorisSyncJdbc") JdbcTemplate jdbc, HddtProperties props) {
        this.jdbc = jdbc;
        this.t = new DorisTables(props.doris().database());
    }

    /** Bucket pruning của Doris 4.0.8 chỉ áp khi IN ≤ 100 giá trị (đo DEV 06/11 — cùng ngưỡng HD8 TD-API-35). */
    static final int MAX_KEYS = 100;

    static final String HEADER_COLUMNS = "h.TDLAP, h.CQT, h.LOAI_HD, h.ID, h.NBMST, h.KHMSHDON, h.KHHDON, h.SHDON, h.NKY,"
            + " h.TTHAI, h.NBTEN, h.TGTCTHUE, h.TGTTHUE, CAST(h.THTTLTSUAT AS STRING) AS THTTLTSUAT_JSON,"
            + " h.BHPHAP, h.BHPLDO, h.BHPNGAY, h.GCHDGOC";

    /**
     * TD-API-36 (08/11, áp cách tra theo khóa literal của HD8 TD-API-35): bước A lấy khóa vật lý từ HD_MST_LIST (partition KY
     * + bucket MST ⇒ 1 tablet); bước B đọc HOADON_HDR bằng giá trị khóa cụ thể (TDLAP, CQT, LOAI_HD, ID IN ≤ 100) ⇒ 1
     * partition, cắt bucket theo ID — thay cho JOIN một câu (phía HDR không có ID literal ⇒ đọc cả partition tháng, chỉ dựa
     * runtime filter). Điều kiện khóa tra cứu chính xác vẫn áp ở HDR như cũ; kết quả và thứ tự (LOAI_HD, ID) không đổi.
     */
    @Override
    public List<InvoiceHeader> findHeaders(String nbmst, int khmshdon, Set<String> khhdonVariants, long shdon,
                                           LocalDate tdlap, int limit) {
        ExportSqlBuilder.BuiltSql keys = headerKeysSql(t, nbmst, khhdonVariants, shdon, tdlap);
        Map<String, List<String>> idsByCqtLoai = new TreeMap<>();
        jdbc.query(keys.sql(), (RowCallbackHandler) rs -> idsByCqtLoai
                .computeIfAbsent(rs.getString("CQT") + "|" + rs.getString("LOAI_HD"), k -> new ArrayList<>())
                .add(rs.getString("ID")), keys.args().toArray());
        List<InvoiceHeader> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : idsByCqtLoai.entrySet()) {
            String[] cqtLoai = g.getKey().split("\\|", 2);
            ExportSqlBuilder.BuiltSql q = headersByKeySql(t, tdlap, cqtLoai[0], cqtLoai[1], g.getValue(), nbmst, khmshdon,
                    shdon, khhdonVariants);
            out.addAll(jdbc.query(q.sql(), (rs, i) -> header(rs), q.args().toArray()));
        }
        out.sort(Comparator.comparing(InvoiceHeader::loaiHd).thenComparing(InvoiceHeader::id));
        return out.size() > Math.max(1, limit) ? new ArrayList<>(out.subList(0, Math.max(1, limit))) : out;
    }

    /** HD3 bước A: khóa vật lý của HĐ bán ra trên HD_MST_LIST (KY = tháng TDLAP, MST = NBMST). */
    static ExportSqlBuilder.BuiltSql headerKeysSql(DorisTables t, String nbmst, Set<String> khhdonVariants, long shdon,
                                                   LocalDate tdlap) {
        String sql = "SELECT l.CQT, l.LOAI_HD, l.ID FROM " + t.list() + " l"
                + " WHERE l.KY = ? AND l.MST = ? AND l.CHIEU = '" + Direction.BAN_RA.name() + "' AND l.TDLAP = ?"
                + " AND l.SHDON = ? AND l.KHHDON IN (" + DorisTables.placeholders(khhdonVariants.size()) + ")"
                + " ORDER BY l.LOAI_HD, l.ID LIMIT " + MAX_KEYS;
        List<Object> args = new ArrayList<>();
        args.add(tdlap.withDayOfMonth(1));
        args.add(nbmst);
        args.add(tdlap);
        args.add(shdon);
        args.addAll(khhdonVariants);
        return new ExportSqlBuilder.BuiltSql(sql, args);
    }

    /** HD3 bước B: HOADON_HDR theo khóa literal (một nhóm CQT, LOAI_HD; ≤ {@value #MAX_KEYS} ID) + khóa tra cứu chính xác. */
    static ExportSqlBuilder.BuiltSql headersByKeySql(DorisTables t, LocalDate tdlap, String cqt, String loaiHd,
                                                     List<String> ids, String nbmst, int khmshdon, long shdon,
                                                     Set<String> khhdonVariants) {
        if (ids.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Quá " + MAX_KEYS + " ID trong một nhánh tra HOADON_HDR");
        }
        String sql = "SELECT " + HEADER_COLUMNS + " FROM " + t.hdr() + " h"
                + " WHERE h.TDLAP = ? AND h.CQT = ? AND h.LOAI_HD = ? AND h.ID IN (" + DorisTables.placeholders(ids.size()) + ")"
                + " AND h.NBMST = ? AND h.KHMSHDON = ? AND h.SHDON = ?"
                + " AND h.KHHDON IN (" + DorisTables.placeholders(khhdonVariants.size()) + ")";
        List<Object> args = new ArrayList<>();
        args.add(tdlap);
        args.add(cqt);
        args.add(loaiHd);
        args.addAll(ids);
        args.add(nbmst);
        args.add(khmshdon);
        args.add(shdon);
        args.addAll(khhdonVariants);
        return new ExportSqlBuilder.BuiltSql(sql, args);
    }

    @Override
    public List<GoodsLine> findGoodsLines(InvoiceHeader h) {
        String sql = "SELECT STT, TEN, THTCTHUE, LTSUAT, TTHUE FROM " + t.dtl()
                + " WHERE TDLAP = ? AND CQT = ? AND LOAI_HD = ? AND IDHDON = ? ORDER BY STT";
        return jdbc.query(sql, (rs, i) -> new GoodsLine(rs.getInt("STT"), rs.getString("TEN"),
                        decimalText(rs.getString("THTCTHUE"), "THTCTHUE"), rs.getString("LTSUAT"),
                        decimalText(rs.getString("TTHUE"), "TTHUE")),
                h.tdlap(), h.cqt(), h.loaiHd(), h.id());
    }

    private InvoiceHeader header(ResultSet rs) throws SQLException {
        return new InvoiceHeader(rs.getObject("TDLAP", LocalDate.class), rs.getString("CQT"), rs.getString("LOAI_HD"),
                rs.getString("ID"), rs.getString("NBMST"), intOrNull(rs, "KHMSHDON"), rs.getString("KHHDON"),
                longOrNull(rs, "SHDON"), dateTime(rs, "NKY"), intOrNull(rs, "TTHAI"), rs.getString("NBTEN"),
                rs.getBigDecimal("TGTCTHUE"), rs.getBigDecimal("TGTTHUE"), taxLines(rs.getString("THTTLTSUAT_JSON")),
                intOrNull(rs, "BHPHAP"), rs.getString("BHPLDO"), dateTime(rs, "BHPNGAY"),
                rs.getString("GCHDGOC")); // lddctthe (API v3) — SI-53: nguồn đề xuất, chờ xác nhận
    }

    /** THTTLTSUAT (variant) = mảng {tsuat, thtien, tthue} giữ nguyên, không flatten (SI-20). NULL ⇒ []. */
    static List<TaxRateLine> taxLines(String json) {
        if (json == null || Jdk8Compat.isBlank(json) || "null".equals(json)) {
            return Jdk8Compat.listOf();
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (!node.isArray()) {
                throw new IllegalStateException("THTTLTSUAT không phải mảng JSON");
            }
            List<TaxRateLine> out = new ArrayList<>();
            for (JsonNode e : node) {
                out.add(new TaxRateLine(text(e.get("tsuat")), dec(e.get("thtien")), dec(e.get("tthue"))));
            }
            return out;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("THTTLTSUAT không đọc được", e);
        }
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    private static BigDecimal dec(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        return n.isNumber() ? n.decimalValue() : decimalText(n.asText(), "THTTLTSUAT");
    }

    /** DTL.THTCTHUE / TTHUE là VARCHAR (SI-34): chỉ nhận số hợp lệ, còn lại null + cảnh báo (không tự thay THTIEN). */
    static BigDecimal decimalText(String s, String column) {
        if (s == null || Jdk8Compat.isBlank(s)) {
            return null;
        }
        String v = s.trim();
        if (!NUMBER.matcher(v).matches()) {
            log.warn("Giá trị {} không phải số, trả null", column);
            return null;
        }
        return new BigDecimal(v);
    }

    static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Long longOrNull(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    /** DATETIME Doris không có múi giờ: đọc nguyên giá trị đồng hồ (không quy đổi theo múi giờ JVM). */
    static LocalDateTime dateTime(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDateTime.class);
    }
}
