package vn.teca.hddt.dataapi.application.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** Tham số job lưu dạng JSON chuẩn tắc (thuộc tính sắp xếp) để băm idempotency ổn định. */
public final class JobPayloads {
    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addModule(new JavaTimeModule())
            .build();

    /** HD1/HD5. */
    public static final class ListExportParams {
        @JsonProperty("direction")
        private final String direction;
        @JsonProperty("mst")
        private final String mst;
        @JsonProperty("tthang")
        private final String tthang;
        @JsonProperty("dthang")
        private final String dthang;
        @JsonProperty("lbttmsthue")
        private final boolean lbttmsthue;
        @JsonProperty("lbbhphap")
        private final boolean lbbhphap;

        @JsonCreator
        public ListExportParams(@JsonProperty("direction") String direction, @JsonProperty("mst") String mst,
                @JsonProperty("tthang") String tthang, @JsonProperty("dthang") String dthang,
                @JsonProperty("lbttmsthue") boolean lbttmsthue, @JsonProperty("lbbhphap") boolean lbbhphap) {
            this.direction = direction;
            this.mst = mst;
            this.tthang = tthang;
            this.dthang = dthang;
            this.lbttmsthue = lbttmsthue;
            this.lbbhphap = lbbhphap;
        }

        public String direction() {
            return direction;
        }

        public String mst() {
            return mst;
        }

        public String tthang() {
            return tthang;
        }

        public String dthang() {
            return dthang;
        }

        public boolean lbttmsthue() {
            return lbttmsthue;
        }

        public boolean lbbhphap() {
            return lbbhphap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ListExportParams)) {
                return false;
            }
            ListExportParams other = (ListExportParams) o;
            return Objects.equals(direction, other.direction)
                    && Objects.equals(mst, other.mst)
                    && Objects.equals(tthang, other.tthang)
                    && Objects.equals(dthang, other.dthang)
                    && lbttmsthue == other.lbttmsthue
                    && lbbhphap == other.lbbhphap;
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(direction);
            result = 31 * result + Objects.hashCode(mst);
            result = 31 * result + Objects.hashCode(tthang);
            result = 31 * result + Objects.hashCode(dthang);
            result = 31 * result + Boolean.hashCode(lbttmsthue);
            result = 31 * result + Boolean.hashCode(lbbhphap);
            return result;
        }

        @Override
        public String toString() {
            return "ListExportParams[direction=" + direction
                    + ", mst=" + mst
                    + ", tthang=" + tthang
                    + ", dthang=" + dthang
                    + ", lbttmsthue=" + lbttmsthue
                    + ", lbbhphap=" + lbbhphap + "]";
        }
    }

    /** HD8. */
    public static final class VerifyParams {
        @JsonProperty("nmmst")
        private final String nmmst;
        @JsonProperty("tngay")
        private final String tngay;
        @JsonProperty("dngay")
        private final String dngay;
        @JsonProperty("s3url")
        private final String s3url;

        @JsonCreator
        public VerifyParams(@JsonProperty("nmmst") String nmmst, @JsonProperty("tngay") String tngay,
                @JsonProperty("dngay") String dngay, @JsonProperty("s3url") String s3url) {
            this.nmmst = nmmst;
            this.tngay = tngay;
            this.dngay = dngay;
            this.s3url = s3url;
        }

        public String nmmst() {
            return nmmst;
        }

        public String tngay() {
            return tngay;
        }

        public String dngay() {
            return dngay;
        }

        public String s3url() {
            return s3url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof VerifyParams)) {
                return false;
            }
            VerifyParams other = (VerifyParams) o;
            return Objects.equals(nmmst, other.nmmst)
                    && Objects.equals(tngay, other.tngay)
                    && Objects.equals(dngay, other.dngay)
                    && Objects.equals(s3url, other.s3url);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(nmmst);
            result = 31 * result + Objects.hashCode(tngay);
            result = 31 * result + Objects.hashCode(dngay);
            result = 31 * result + Objects.hashCode(s3url);
            return result;
        }

        @Override
        public String toString() {
            return "VerifyParams[nmmst=" + nmmst
                    + ", tngay=" + tngay
                    + ", dngay=" + dngay
                    + ", s3url=" + s3url + "]";
        }
    }

    /** TMS. */
    public static final class RefundParams {
        @JsonProperty("s3url")
        private final String s3url;

        @JsonCreator
        public RefundParams(@JsonProperty("s3url") String s3url) {
            this.s3url = s3url;
        }

        public String s3url() {
            return s3url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RefundParams)) {
                return false;
            }
            RefundParams other = (RefundParams) o;
            return Objects.equals(s3url, other.s3url);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(s3url);
            return result;
        }

        @Override
        public String toString() {
            return "RefundParams[s3url=" + s3url + "]";
        }
    }

    private JobPayloads() {
    }

    public static String toJson(Object params) {
        try {
            return CANONICAL.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tuần tự hóa được tham số job", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return CANONICAL.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Tham số job hỏng: " + type.getSimpleName(), e);
        }
    }

    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Jdk8Compat.formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ObjectMapper mapper() {
        return CANONICAL;
    }
}
