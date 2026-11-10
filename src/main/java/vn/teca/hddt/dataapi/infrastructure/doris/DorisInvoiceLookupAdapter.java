package vn.teca.hddt.dataapi.infrastructure.doris;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongPredicate;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort;
import vn.teca.hddt.dataapi.application.port.PurchaseCandidates;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceRecord;

/**
 * Tra theo lô (HD8, TMS): một truy vấn cho cả lô với IN theo NBMST/SHDON trên HD_MST_LIST (cắt bucket theo MST),
 * nối HOADON_HDR theo khóa; lọc khóa chính xác (KHMSHDON, KHHDON) ở Java vì Doris không hỗ trợ IN theo bộ giá trị.
 * {@code hddt.doris.list-serving} (DB-P1-03, TD-API-34): đọc thẳng cột HD_MST_LIST DDL v5, không nối HOADON_HDR — áp cho TMS;
 * bản ghi HD8 v3 (SI-65) cần THTTLTSUAT/GCHDGOC chỉ có ở HDR ⇒ vẫn đọc HDR ({@link #purchasesFromList}, KI-API-32).
 * HD8 DB-P1-02 (TD-API-35): {@link #indexPurchases} một truy vấn luồng/job + {@link #fetchPurchases} theo khóa literal mỗi lô.
 */
@Component
public class DorisInvoiceLookupAdapter implements InvoiceLookupPort {
    private final JdbcTemplate jdbc;
    private final DorisTables t;
    private final boolean listServing;
    /** HD8 đọc bản ghi từ HD_MST_LIST chỉ khi LIST đủ cột (SI-65: cần THTTLTSUAT/GCHDGOC của HDR — KI-API-32). */
    private final boolean purchasesFromList;

    public DorisInvoiceLookupAdapter(@Qualifier("dorisLookupJdbc") JdbcTemplate jdbc, HddtProperties props) {
        this.jdbc = jdbc;
        this.t = new DorisTables(props.doris().database());
        this.listServing = props.doris().listServing();
        this.purchasesFromList = listServing && InvoiceRecordColumns.LIST_COVERS_RECORD;
    }

    /** HD8: HĐ mua vào của người mua trong khoảng TDLAP, IN theo người bán / số HĐ của lô. */
    static String purchasesSql(DorisTables t, boolean fromList, int sellers, int numbers) {
        String where = " WHERE l.MST = ? AND l.CHIEU = '" + Direction.MUA_VAO.name() + "' AND l.IS_ELIGIBLE = 1"
                + " AND l.TDLAP >= ? AND l.TDLAP <= ? AND l.NBMST IN (" + DorisTables.placeholders(sellers) + ")"
                + " AND l.SHDON IN (" + DorisTables.placeholders(numbers) + ")";
        if (fromList) {
            return "SELECT " + InvoiceRecordColumns.LIST_SELECT + " FROM " + t.list() + " l" + where;
        }
        return "SELECT " + InvoiceRecordColumns.SELECT + " FROM " + t.list() + " l JOIN " + t.hdr() + " h ON "
                + DorisTables.LIST_HDR_JOIN + where + " AND h.TDLAP >= ? AND h.TDLAP <= ?";
    }

    /** Bucket pruning của Doris 4.0.8 chỉ áp khi IN ≤ 100 giá trị (đo DEV 06/11). */
    static final int MAX_IDS_PER_BRANCH = 100;
    /** Giới hạn nhánh UNION ALL mỗi câu (lô 500 khóa trải nhiều ngày lập ⇒ tách câu, giữ câu SQL vừa phải). */
    static final int MAX_BRANCHES_PER_STATEMENT = 50;

    /** HD8 bước A: ứng viên của người mua trong khoảng TDLAP — mỗi partition KY 1 tablet (prefix KY, MST). */
    static String candidatesSql(DorisTables t) {
        return "SELECT l.NBMST, l.KHHDON, l.SHDON, l.TDLAP, l.CQT, l.LOAI_HD, l.ID, l.KY FROM " + t.list() + " l"
                + " WHERE l.MST = ? AND l.CHIEU = '" + Direction.MUA_VAO.name() + "' AND l.IS_ELIGIBLE = 1"
                + " AND l.TDLAP >= ? AND l.TDLAP <= ?";
    }

    /**
     * HD8 bước B: một nhánh mỗi (TDLAP, CQT, LOAI_HD[, KY]) × ≤ {@value #MAX_IDS_PER_BRANCH} ID, ≤
     * {@value #MAX_BRANCHES_PER_STATEMENT} nhánh UNION ALL mỗi câu. HDR: 1 partition, bucket theo ID; LIST v5 (list-serving):
     * khóa KY + MST + … ⇒ 1 tablet.
     */
    static List<ExportSqlBuilder.BuiltSql> fetchStatements(DorisTables t, boolean fromList, String nmmst,
                                                          Collection<PurchaseCandidates.Ref> refs) {
        Map<String, List<PurchaseCandidates.Ref>> groups = new TreeMap<>();
        for (PurchaseCandidates.Ref r : refs) {
            groups.computeIfAbsent(r.tdlap() + "|" + r.cqt() + "|" + r.loaiHd() + "|" + (fromList ? r.ky() : ""),
                    k -> new ArrayList<>()).add(r);
        }
        List<String> branches = new ArrayList<>();
        List<List<Object>> branchArgs = new ArrayList<>();
        for (List<PurchaseCandidates.Ref> g : groups.values()) {
            for (int from = 0; from < g.size(); from += MAX_IDS_PER_BRANCH) {
                List<PurchaseCandidates.Ref> chunk = g.subList(from, Math.min(g.size(), from + MAX_IDS_PER_BRANCH));
                PurchaseCandidates.Ref first = chunk.get(0);
                List<Object> a = new ArrayList<>();
                String sql;
                if (fromList) {
                    sql = "SELECT " + InvoiceRecordColumns.LIST_SELECT + " FROM " + t.list() + " l WHERE l.KY = ? AND l.MST = ?"
                            + " AND l.CHIEU = '" + Direction.MUA_VAO.name() + "' AND l.IS_ELIGIBLE = 1 AND l.TDLAP = ?"
                            + " AND l.LOAI_HD = ? AND l.CQT = ? AND l.ID IN (" + DorisTables.placeholders(chunk.size()) + ")";
                    a.add(first.ky());
                    a.add(nmmst);
                    a.add(first.tdlap());
                    a.add(first.loaiHd());
                    a.add(first.cqt());
                } else {
                    sql = "SELECT " + InvoiceRecordColumns.SELECT + " FROM " + t.hdr() + " h WHERE h.TDLAP = ? AND h.CQT = ?"
                            + " AND h.LOAI_HD = ? AND h.ID IN (" + DorisTables.placeholders(chunk.size()) + ")";
                    a.add(first.tdlap());
                    a.add(first.cqt());
                    a.add(first.loaiHd());
                }
                chunk.forEach(r -> a.add(r.id()));
                branches.add(sql);
                branchArgs.add(a);
            }
        }
        List<ExportSqlBuilder.BuiltSql> out = new ArrayList<>();
        for (int from = 0; from < branches.size(); from += MAX_BRANCHES_PER_STATEMENT) {
            int to = Math.min(branches.size(), from + MAX_BRANCHES_PER_STATEMENT);
            List<Object> args = new ArrayList<>();
            for (int i = from; i < to; i++) {
                args.addAll(branchArgs.get(i));
            }
            out.add(new ExportSqlBuilder.BuiltSql(String.join(" UNION ALL ", branches.subList(from, to)), args));
        }
        return out;
    }

    /** Cùng điều kiện bước A, chỉ đếm phía Doris. */
    static String countCandidatesSql(DorisTables t) {
        String a = candidatesSql(t);
        return "SELECT COUNT(*) FROM " + t.list() + " l" + a.substring(a.indexOf(" WHERE "));
    }

    @Override
    public long countPurchaseCandidates(String nmmst, LocalDate tngay, LocalDate dngay) {
        Long n = jdbc.queryForObject(countCandidatesSql(t), Long.class, nmmst, tngay, dngay);
        return n == null ? 0 : n;
    }

    @Override
    public PurchaseCandidates indexPurchases(String nmmst, LocalDate tngay, LocalDate dngay, LongPredicate wanted) {
        PurchaseCandidates idx = new PurchaseCandidates();
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(candidatesSql(t), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE); // Connector/J: đọc luồng, không giữ cả kết quả trong bộ nhớ
            ps.setString(1, nmmst);
            ps.setObject(2, tngay);
            ps.setObject(3, dngay);
            return ps;
        }, (RowCallbackHandler) rs -> {
            String nbmst = rs.getString(1);
            String khhdon = rs.getString(2);
            long shdon = rs.getLong(3);
            if (rs.wasNull() || nbmst == null || khhdon == null) {
                return;
            }
            long fp = InvoiceKey.lookupFingerprint(nbmst, khhdon, shdon);
            if (wanted.test(fp)) {
                idx.add(fp, rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getObject(8, LocalDate.class));
            }
        });
        return idx;
    }

    @Override
    public List<InvoiceRecord> fetchPurchases(String nmmst, PurchaseCandidates index, Collection<InvoiceKey> keys) {
        Collection<PurchaseCandidates.Ref> refs = index.refsFor(keys);
        if (refs.isEmpty()) {
            return Jdk8Compat.listOf();
        }
        Set<InvoiceKey> wanted = new HashSet<>(keys);
        List<InvoiceRecord> out = new ArrayList<>();
        for (ExportSqlBuilder.BuiltSql q : fetchStatements(t, purchasesFromList, nmmst, refs)) {
            jdbc.query(q.sql(), InvoiceRecordColumns.MAPPER, q.args().toArray()).stream()
                    .filter(r -> wanted.contains(r.key())).forEach(out::add);
        }
        return out;
    }

    /** TMS: HĐ bán ra của tháng theo người bán / số HĐ / ngày lập. */
    static String refundTargetsSql(DorisTables t, boolean fromList, int sellers, int numbers, int dates) {
        String a = fromList ? "l" : "h";
        String sql = "SELECT " + a + ".NBMST, " + a + ".KHMSHDON, " + a + ".KHHDON, " + a + ".SHDON, " + a + ".TDLAP, "
                + a + ".LOAI_HD, " + a + ".CQT, " + a + ".ID, " + a + ".NMMST FROM " + t.list() + " l"
                + (fromList ? "" : " JOIN " + t.hdr() + " h ON " + DorisTables.LIST_HDR_JOIN)
                + " WHERE l.KY = ? AND l.CHIEU = '" + Direction.BAN_RA.name() + "'"
                + " AND l.MST IN (" + DorisTables.placeholders(sellers) + ")"
                + " AND l.SHDON IN (" + DorisTables.placeholders(numbers) + ")"
                + " AND l.TDLAP IN (" + DorisTables.placeholders(dates) + ")";
        return fromList ? sql : sql + " AND h.TDLAP IN (" + DorisTables.placeholders(dates) + ")";
    }

    @Override
    public List<InvoiceRecord> findPurchases(String nmmst, LocalDate tngay, LocalDate dngay, Collection<InvoiceKey> keys) {
        if (keys.isEmpty()) {
            return Jdk8Compat.listOf();
        }
        Set<String> sellers = new LinkedHashSet<>();
        Set<Long> numbers = new LinkedHashSet<>();
        keys.forEach(k -> {
            sellers.add(k.nbmst());
            numbers.add(k.shdon());
        });
        String sql = purchasesSql(t, purchasesFromList, sellers.size(), numbers.size());
        List<Object> args = new ArrayList<>();
        args.add(nmmst);
        args.add(tngay);
        args.add(dngay);
        args.addAll(sellers);
        args.addAll(numbers);
        if (!purchasesFromList) {
            args.add(tngay);
            args.add(dngay);
        }
        Set<InvoiceKey> wanted = new HashSet<>(keys);
        return jdbc.query(sql, InvoiceRecordColumns.MAPPER, args.toArray()).stream()
                .filter(r -> wanted.contains(r.key())).collect(Jdk8Compat.toList());
    }

    @Override
    public List<RefundTarget> findRefundTargets(Collection<DatedKey> keys) {
        Map<LocalDate, List<DatedKey>> byPeriod = new TreeMap<>();
        for (DatedKey k : keys) {
            byPeriod.computeIfAbsent(k.tdlap().withDayOfMonth(1), p -> new ArrayList<>()).add(k);
        }
        Set<InvoiceKey> wanted = new HashSet<>();
        keys.forEach(k -> wanted.add(k.key()));
        List<RefundTarget> out = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DatedKey>> e : byPeriod.entrySet()) {
            Set<String> sellers = new LinkedHashSet<>();
            Set<Long> numbers = new LinkedHashSet<>();
            Set<LocalDate> dates = new LinkedHashSet<>();
            e.getValue().forEach(k -> {
                sellers.add(k.key().nbmst());
                numbers.add(k.key().shdon());
                dates.add(k.tdlap());
            });
            String sql = refundTargetsSql(t, listServing, sellers.size(), numbers.size(), dates.size());
            List<Object> args = new ArrayList<>();
            args.add(e.getKey());
            args.addAll(sellers);
            args.addAll(numbers);
            args.addAll(dates);
            if (!listServing) {
                args.addAll(dates);
            }
            jdbc.query(sql, (rs, i) -> new RefundTarget(new InvoiceKey(rs.getString("NBMST"), rs.getInt("KHMSHDON"),
                            rs.getString("KHHDON"), rs.getLong("SHDON")), rs.getObject("TDLAP", LocalDate.class),
                            rs.getString("LOAI_HD"), rs.getString("CQT"), rs.getString("ID"), rs.getString("NMMST")),
                    args.toArray()).stream().filter(r -> wanted.contains(r.key())).forEach(out::add);
        }
        return out;
    }
}
