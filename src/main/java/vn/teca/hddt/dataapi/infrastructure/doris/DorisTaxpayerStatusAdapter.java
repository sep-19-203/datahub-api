package vn.teca.hddt.dataapi.infrastructure.doris;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import vn.teca.hddt.dataapi.application.port.TaxpayerStatusPort;
import vn.teca.hddt.dataapi.config.HddtProperties;

/**
 * Trạng thái MST (HD3/HD8/TMS) đọc từ nguồn SQL cấu hình — mặc định {@code SELECT MST, MST_CU, TRANG_THAI FROM
 * doris.NNT_ALL_STATUS}; MST không thấy thì tra theo MST cũ (SI-65, {@link MstStatusSql}).
 */
@Component
public class DorisTaxpayerStatusAdapter implements TaxpayerStatusPort {
    private static final int CHUNK = 1000;

    private final JdbcTemplate jdbc;
    private final HddtProperties.MstStatus cfg;

    public DorisTaxpayerStatusAdapter(@Qualifier("dorisSyncJdbc") JdbcTemplate jdbc, HddtProperties props) {
        this.jdbc = jdbc;
        this.cfg = props.mstStatus();
    }

    @Override
    public boolean configured() {
        return cfg.configured();
    }

    @Override
    public Optional<String> findStatus(String mst) {
        List<String> r = jdbc.queryForList(statusSql(cfg, "s.TRANG_THAI", 1), String.class, mst, mst, mst);
        return r.isEmpty() ? Optional.empty() : Optional.ofNullable(r.get(0));
    }

    @Override
    public Map<String, String> findStatuses(Collection<String> msts) {
        Map<String, String> out = new HashMap<>();
        List<String> all = new ArrayList<>(msts);
        for (int i = 0; i < all.size(); i += CHUNK) {
            List<String> part = all.subList(i, Math.min(all.size(), i + CHUNK));
            List<Object> args = new ArrayList<>(part.size() * 3);
            for (int k = 0; k < 3; k++) {
                args.addAll(part);
            }
            jdbc.query(statusSql(cfg, "s.MST, s.TRANG_THAI", part.size()), rs -> {
                out.put(rs.getString(1), rs.getString(2));
            }, args.toArray());
        }
        return out;
    }

    /**
     * Trạng thái của {@code n} MST — tra cả MST cũ (SI-65). Danh sách MST lọc nguồn trước khi gộp (TD-API-39) ở 2 nhánh
     * và ở WHERE ngoài ⇒ mỗi lần gọi truyền danh sách 3 lần.
     */
    static String statusSql(HddtProperties.MstStatus cfg, String columns, int n) {
        String in = "(" + DorisTables.placeholders(n) + ")";
        return "SELECT " + columns + " FROM " + MstStatusSql.derivedTable(cfg, in) + " s WHERE s.MST IN " + in;
    }
}
