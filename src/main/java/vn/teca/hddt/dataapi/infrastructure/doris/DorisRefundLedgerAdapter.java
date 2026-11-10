package vn.teca.hddt.dataapi.infrastructure.doris;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.port.InvoiceLookupPort.DatedKey;
import vn.teca.hddt.dataapi.application.port.RefundLedgerPort;
import vn.teca.hddt.dataapi.config.HddtProperties;
import vn.teca.hddt.dataapi.domain.common.DateFormats;
import vn.teca.hddt.dataapi.domain.invoice.InvoiceKey;

/**
 * Sổ hoàn trên Doris ({@code HD_SO_HOAN}, UNIQUE KEY TDLAP + khóa hóa đơn + MCV, MOW). Ghi bằng INSERT … WITH LABEL theo
 * khối: chạy lại cùng (MCV, khối) ⇒ Doris báo label đã dùng ⇒ coi như đã ghi; hết hạn giữ label thì INSERT ghi đè đúng
 * các khóa cũ (idempotent). Tài khoản Doris của dịch vụ chỉ cần quyền INSERT/SELECT trên riêng bảng này.
 */
@Component
public class DorisRefundLedgerAdapter implements RefundLedgerPort {
    private static final Logger log = LoggerFactory.getLogger(DorisRefundLedgerAdapter.class);
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern MCV = Pattern.compile("[0-9a-fA-F-]{36}");
    static final String[] COLUMNS = {"TDLAP", "NBMST", "KHMSHDON", "KHHDON", "SHDON", "MCV", "TTHOAN", "LOAI_HD", "CQT",
            "ID", "MSTHSO", "NGUON_MSTHSO", "TGTCTHUE", "TGTTHUE", "THTTLTSUAT", "TTMSTNBAN", "TTHAI", "NKY", "STT_DONG",
            "NGUON_FILE", "SHA_FILE", "CLIENT_ID", "TDGHI"};

    private final JdbcTemplate jdbc;
    private final String table;

    public DorisRefundLedgerAdapter(@Qualifier("dorisLookupJdbc") JdbcTemplate jdbc, HddtProperties props) {
        this.jdbc = jdbc;
        String db = props.doris().database();
        String tbl = props.tms().ledgerTable();
        if (!IDENT.matcher(db).matches() || !IDENT.matcher(tbl).matches()) {
            throw new IllegalArgumentException("Tên bảng sổ hoàn không hợp lệ");
        }
        this.table = "`" + db + "`.`" + tbl + "`";
    }

    @Override
    public Map<InvoiceKey, String> findActiveRefunds(Collection<DatedKey> keys, String excludeMcv) {
        Map<InvoiceKey, String> out = new HashMap<>();
        if (keys.isEmpty()) {
            return out;
        }
        Set<LocalDate> dates = new LinkedHashSet<>();
        Set<String> sellers = new LinkedHashSet<>();
        Set<Long> numbers = new LinkedHashSet<>();
        Map<InvoiceKey, LocalDate> wanted = new HashMap<>();
        for (DatedKey k : keys) {
            dates.add(k.tdlap());
            sellers.add(k.key().nbmst());
            numbers.add(k.key().shdon());
            wanted.put(k.key(), k.tdlap());
        }
        String sql = "SELECT NBMST, KHMSHDON, KHHDON, SHDON, TDLAP, MCV FROM " + table
                + " WHERE TDLAP IN (" + DorisTables.placeholders(dates.size()) + ")"
                + " AND NBMST IN (" + DorisTables.placeholders(sellers.size()) + ")"
                + " AND SHDON IN (" + DorisTables.placeholders(numbers.size()) + ")"
                + " AND TTHOAN = 'DA_HOAN' AND MCV <> ?";
        List<Object> args = new ArrayList<>(dates);
        args.addAll(sellers);
        args.addAll(numbers);
        args.add(excludeMcv);
        jdbc.query(sql, rs -> {
            InvoiceKey k = new InvoiceKey(rs.getString("NBMST"), rs.getInt("KHMSHDON"), rs.getString("KHHDON"),
                    rs.getLong("SHDON"));
            if (rs.getObject("TDLAP", LocalDate.class).equals(wanted.get(k))) {
                out.putIfAbsent(k, rs.getString("MCV"));
            }
        }, args.toArray());
        return out;
    }

    @Override
    public void write(String mcv, int chunkNo, List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        if (!MCV.matcher(mcv).matches()) {
            throw new IllegalArgumentException("MCV không hợp lệ");
        }
        String label = "sohoan_" + mcv.replace("-", "") + "_" + chunkNo;
        String row = "(" + String.join(", ", java.util.Collections.nCopies(COLUMNS.length, "?")) + ")";
        String sql = "INSERT INTO " + table + " WITH LABEL " + label + " (" + String.join(", ", COLUMNS) + ") VALUES "
                + String.join(", ", java.util.Collections.nCopies(entries.size(), row));
        LocalDateTime now = LocalDateTime.now(DateFormats.VN).withNano(0);
        List<Object> args = new ArrayList<>(entries.size() * COLUMNS.length);
        for (Entry e : entries) {
            args.add(e.tdlap());
            args.add(e.nbmst());
            args.add(e.khmshdon());
            args.add(e.khhdon());
            args.add(e.shdon());
            args.add(e.mcv());
            args.add("DA_HOAN");
            args.add(e.loaiHd());
            args.add(e.cqt());
            args.add(e.id());
            args.add(e.msthso());
            args.add(e.msthso() == null ? null : "HD_NMMST");
            args.add(e.tgtcthue());
            args.add(e.tgtthue());
            args.add(e.thttltsuat());
            args.add(e.ttmstnban());
            args.add(e.tthai());
            args.add(e.nky());
            args.add(e.sttDong());
            args.add(e.nguonFile());
            args.add(e.shaFile());
            args.add(e.clientId());
            args.add(now);
        }
        try {
            jdbc.update(sql, args.toArray());
        } catch (DataAccessException ex) {
            String m = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (m.contains("has already been used") || m.contains("already exist")) {
                log.info("Khối sổ hoàn {} đã được ghi trước đó (label {})", chunkNo, label);
                return;
            }
            throw ex;
        }
    }

    @Override
    public long countActive(String mcv, LocalDate minTdlap, LocalDate maxTdlap) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE TDLAP >= ? AND TDLAP <= ? AND MCV = ? AND TTHOAN = 'DA_HOAN'", Long.class, minTdlap, maxTdlap, mcv);
        return n == null ? 0 : n;
    }
}
