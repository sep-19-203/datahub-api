package vn.teca.hddt.dataapi.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Trạng thái MST (SI-24): nguồn ngoài datahub_hddt, cấu hình qua {@code hddt.mst-status}. */
public interface TaxpayerStatusPort {
    boolean configured();

    Optional<String> findStatus(String mst);

    Map<String, String> findStatuses(Collection<String> msts);
}
