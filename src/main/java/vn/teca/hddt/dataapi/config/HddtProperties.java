package vn.teca.hddt.dataapi.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import vn.teca.hddt.dataapi.application.security.ApiPermission;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;
import vn.teca.hddt.dataapi.domain.hd7.SalesMappingPolicy.UnassignedCodePolicy;
import vn.teca.hddt.dataapi.domain.invoice.Direction;
import vn.teca.hddt.dataapi.domain.job.JobType;

/** Toàn bộ cấu hình nghiệp vụ/kỹ thuật của dịch vụ (tiền tố {@code hddt}). Secret chỉ lấy từ biến môi trường / Secret. */
@ConfigurationProperties("hddt")
@ConstructorBinding
public final class HddtProperties {
    private final Api api;
    private final Doris doris;
    private final Dse dse;
    private final S3 s3;
    private final MstStatus mstStatus;
    private final Hd1 hd1;
    private final Hd5 hd5;
    private final Hd7 hd7;
    private final Hd8 hd8;
    private final Tms tms;
    private final Export export;
    private final Worker worker;
    private final Security security;
    private final Map<JobType, String> jobCodes;

    public HddtProperties(@DefaultValue Api api, @DefaultValue Doris doris, @DefaultValue Dse dse, @DefaultValue S3 s3,
            @DefaultValue MstStatus mstStatus, @DefaultValue Hd1 hd1, @DefaultValue Hd5 hd5, @DefaultValue Hd7 hd7,
            @DefaultValue Hd8 hd8, @DefaultValue Tms tms,
            @DefaultValue Export export, @DefaultValue Worker worker, @DefaultValue Security security,
            Map<JobType, String> jobCodes) {
        this.api = api;
        this.doris = doris;
        this.dse = dse;
        this.s3 = s3;
        this.mstStatus = mstStatus;
        this.hd1 = hd1;
        this.hd5 = hd5;
        this.hd7 = hd7;
        this.hd8 = hd8;
        this.tms = tms;
        this.export = export;
        this.worker = worker;
        this.security = security;
        this.jobCodes = jobCodes;
    }

    public Api api() {
        return api;
    }

    public Doris doris() {
        return doris;
    }

    public Dse dse() {
        return dse;
    }

    public S3 s3() {
        return s3;
    }

    public MstStatus mstStatus() {
        return mstStatus;
    }

    public Hd1 hd1() {
        return hd1;
    }

    public Hd5 hd5() {
        return hd5;
    }

    public Hd7 hd7() {
        return hd7;
    }

    public Hd8 hd8() {
        return hd8;
    }

    public Tms tms() {
        return tms;
    }

    public Export export() {
        return export;
    }

    public Worker worker() {
        return worker;
    }

    public Security security() {
        return security;
    }

    public Map<JobType, String> jobCodes() {
        return jobCodes;
    }

    public String jobCode(JobType type) {
        String c = jobCodes == null ? null : jobCodes.get(type);
        return c == null || Jdk8Compat.isBlank(c) ? type.defaultCode() : c;
    }

    @ConstructorBinding
    public static final class Api {
        private final int maxMonths;
        /** SI-03: TMS!Q21 = 200; đổi 202 khi hợp đồng thống nhất. */
        private final int tmsAcceptedHttpStatus;
        private final long maxRequestBytes;
        private final int maxActiveJobsPerClient;
        private final int maxActiveJobsPerMst;

        public Api(@DefaultValue("12") int maxMonths, @DefaultValue("200") int tmsAcceptedHttpStatus,
                @DefaultValue("65536") long maxRequestBytes, @DefaultValue("20") int maxActiveJobsPerClient,
                @DefaultValue("3") int maxActiveJobsPerMst) {
            this.maxMonths = maxMonths;
            this.tmsAcceptedHttpStatus = tmsAcceptedHttpStatus;
            this.maxRequestBytes = maxRequestBytes;
            this.maxActiveJobsPerClient = maxActiveJobsPerClient;
            this.maxActiveJobsPerMst = maxActiveJobsPerMst;
        }

        public int maxMonths() {
            return maxMonths;
        }

        public int tmsAcceptedHttpStatus() {
            return tmsAcceptedHttpStatus;
        }

        public long maxRequestBytes() {
            return maxRequestBytes;
        }

        public int maxActiveJobsPerClient() {
            return maxActiveJobsPerClient;
        }

        public int maxActiveJobsPerMst() {
            return maxActiveJobsPerMst;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Api)) {
                return false;
            }
            Api other = (Api) o;
            return maxMonths == other.maxMonths
                    && tmsAcceptedHttpStatus == other.tmsAcceptedHttpStatus
                    && maxRequestBytes == other.maxRequestBytes
                    && maxActiveJobsPerClient == other.maxActiveJobsPerClient
                    && maxActiveJobsPerMst == other.maxActiveJobsPerMst;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(maxMonths);
            result = 31 * result + Integer.hashCode(tmsAcceptedHttpStatus);
            result = 31 * result + Long.hashCode(maxRequestBytes);
            result = 31 * result + Integer.hashCode(maxActiveJobsPerClient);
            result = 31 * result + Integer.hashCode(maxActiveJobsPerMst);
            return result;
        }

        @Override
        public String toString() {
            return "Api[maxMonths=" + maxMonths
                    + ", tmsAcceptedHttpStatus=" + tmsAcceptedHttpStatus
                    + ", maxRequestBytes=" + maxRequestBytes
                    + ", maxActiveJobsPerClient=" + maxActiveJobsPerClient
                    + ", maxActiveJobsPerMst=" + maxActiveJobsPerMst + "]";
        }
    }

    @ConstructorBinding
    public static final class Doris {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String database;
        private final int poolSize;
        private final int syncQueryTimeoutSeconds;
        private final int exportQueryTimeoutSeconds;
        private final int lookupQueryTimeoutSeconds;
        private final boolean listServing;

        /**
         * @param listServing {@code hddt.doris.list-serving} (DB-P1-03, TD-API-34): HD1/HD5/HD8/TMS đọc cột từ HD_MST_LIST
         *                    DDL v5 (KHMSHDON, TEN_DOI_TAC, BHP*, tiền 21,6), không JOIN HOADON_HDR. Chỉ bật khi LIST đã là
         *                    v5 và đối soát LIST ↔ HDR (toolkit LIST_CONTENT_VS_HDR) = 0 lệch; mặc định false (DDL ≤ v4).
         */
        public Doris(String jdbcUrl, String username, String password, @DefaultValue("datahub_hddt") String database,
                @DefaultValue("20") int poolSize, @DefaultValue("30") int syncQueryTimeoutSeconds,
                @DefaultValue("7200") int exportQueryTimeoutSeconds, @DefaultValue("600") int lookupQueryTimeoutSeconds,
                @DefaultValue("false") boolean listServing) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.database = database;
            this.poolSize = poolSize;
            this.syncQueryTimeoutSeconds = syncQueryTimeoutSeconds;
            this.exportQueryTimeoutSeconds = exportQueryTimeoutSeconds;
            this.lookupQueryTimeoutSeconds = lookupQueryTimeoutSeconds;
            this.listServing = listServing;
        }

        public String jdbcUrl() {
            return jdbcUrl;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public String database() {
            return database;
        }

        public int poolSize() {
            return poolSize;
        }

        public int syncQueryTimeoutSeconds() {
            return syncQueryTimeoutSeconds;
        }

        public int exportQueryTimeoutSeconds() {
            return exportQueryTimeoutSeconds;
        }

        public int lookupQueryTimeoutSeconds() {
            return lookupQueryTimeoutSeconds;
        }

        public boolean listServing() {
            return listServing;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Doris)) {
                return false;
            }
            Doris other = (Doris) o;
            return Objects.equals(jdbcUrl, other.jdbcUrl)
                    && Objects.equals(username, other.username)
                    && Objects.equals(password, other.password)
                    && Objects.equals(database, other.database)
                    && poolSize == other.poolSize
                    && syncQueryTimeoutSeconds == other.syncQueryTimeoutSeconds
                    && exportQueryTimeoutSeconds == other.exportQueryTimeoutSeconds
                    && lookupQueryTimeoutSeconds == other.lookupQueryTimeoutSeconds
                    && listServing == other.listServing;
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(jdbcUrl);
            result = 31 * result + Objects.hashCode(username);
            result = 31 * result + Objects.hashCode(password);
            result = 31 * result + Objects.hashCode(database);
            result = 31 * result + Integer.hashCode(poolSize);
            result = 31 * result + Integer.hashCode(syncQueryTimeoutSeconds);
            result = 31 * result + Integer.hashCode(exportQueryTimeoutSeconds);
            result = 31 * result + Integer.hashCode(lookupQueryTimeoutSeconds);
            result = 31 * result + Boolean.hashCode(listServing);
            return result;
        }

        @Override
        public String toString() {
            return "Doris[jdbcUrl=" + jdbcUrl
                    + ", username=" + username
                    + ", password=" + password
                    + ", database=" + database
                    + ", poolSize=" + poolSize
                    + ", syncQueryTimeoutSeconds=" + syncQueryTimeoutSeconds
                    + ", exportQueryTimeoutSeconds=" + exportQueryTimeoutSeconds
                    + ", lookupQueryTimeoutSeconds=" + lookupQueryTimeoutSeconds
                    + ", listServing=" + listServing + "]";
        }
    }

    /** Kho metadata job trên DSE (TD-API-03b). Credential qua biến môi trường (DSE_USERNAME / DSE_PASSWORD). */
    @ConstructorBinding
    public static final class Dse {
        private final List<String> contactPoints;
        private final String localDatacenter;
        private final String username;
        private final String password;
        private final String keyspace;
        /** Map replication CQL, dùng khi tạo keyspace. */
        private final String replication;
        /** Tự tạo keyspace/bảng (IF NOT EXISTS) lúc khởi động — bật ở DEV/local. */
        private final boolean createSchema;
        private final int jobTtlSeconds;
        private final int activeShards;
        private final Duration requestTimeout;
        private final Duration connectTimeout;

        public Dse(@DefaultValue("127.0.0.1:9042") List<String> contactPoints,
                @DefaultValue("DC1") String localDatacenter, String username, String password,
                @DefaultValue("datahub_hddt") String keyspace,
                @DefaultValue("{'class': 'NetworkTopologyStrategy', 'DC1': '2'}") String replication,
                @DefaultValue("false") boolean createSchema, @DefaultValue("10368000") int jobTtlSeconds,
                @DefaultValue("8") int activeShards, @DefaultValue("PT10S") Duration requestTimeout,
                @DefaultValue("PT10S") Duration connectTimeout) {
            this.contactPoints = contactPoints;
            this.localDatacenter = localDatacenter;
            this.username = username;
            this.password = password;
            this.keyspace = keyspace;
            this.replication = replication;
            this.createSchema = createSchema;
            this.jobTtlSeconds = jobTtlSeconds;
            this.activeShards = activeShards;
            this.requestTimeout = requestTimeout;
            this.connectTimeout = connectTimeout;
        }

        public List<String> contactPoints() {
            return contactPoints;
        }

        public String localDatacenter() {
            return localDatacenter;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public String keyspace() {
            return keyspace;
        }

        public String replication() {
            return replication;
        }

        public boolean createSchema() {
            return createSchema;
        }

        public int jobTtlSeconds() {
            return jobTtlSeconds;
        }

        public int activeShards() {
            return activeShards;
        }

        public Duration requestTimeout() {
            return requestTimeout;
        }

        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Dse)) {
                return false;
            }
            Dse other = (Dse) o;
            return Objects.equals(contactPoints, other.contactPoints)
                    && Objects.equals(localDatacenter, other.localDatacenter)
                    && Objects.equals(username, other.username)
                    && Objects.equals(password, other.password)
                    && Objects.equals(keyspace, other.keyspace)
                    && Objects.equals(replication, other.replication)
                    && createSchema == other.createSchema
                    && jobTtlSeconds == other.jobTtlSeconds
                    && activeShards == other.activeShards
                    && Objects.equals(requestTimeout, other.requestTimeout)
                    && Objects.equals(connectTimeout, other.connectTimeout);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(contactPoints);
            result = 31 * result + Objects.hashCode(localDatacenter);
            result = 31 * result + Objects.hashCode(username);
            result = 31 * result + Objects.hashCode(password);
            result = 31 * result + Objects.hashCode(keyspace);
            result = 31 * result + Objects.hashCode(replication);
            result = 31 * result + Boolean.hashCode(createSchema);
            result = 31 * result + Integer.hashCode(jobTtlSeconds);
            result = 31 * result + Integer.hashCode(activeShards);
            result = 31 * result + Objects.hashCode(requestTimeout);
            result = 31 * result + Objects.hashCode(connectTimeout);
            return result;
        }

        @Override
        public String toString() {
            return "Dse[contactPoints=" + contactPoints
                    + ", localDatacenter=" + localDatacenter
                    + ", username=" + username
                    + ", password=" + password
                    + ", keyspace=" + keyspace
                    + ", replication=" + replication
                    + ", createSchema=" + createSchema
                    + ", jobTtlSeconds=" + jobTtlSeconds
                    + ", activeShards=" + activeShards
                    + ", requestTimeout=" + requestTimeout
                    + ", connectTimeout=" + connectTimeout + "]";
        }
    }

    @ConstructorBinding
    public static final class S3 {
        private final String endpoint;
        private final String region;
        private final String accessKey;
        private final String secretKey;
        private final boolean pathStyle;
        private final String exportBucket;
        private final String exportPrefix;
        private final String verifyResultPrefix;
        private final String internalPrefix;
        /** Whitelist đầu vào s3url (SI-07): bucket + tiền tố khóa cho từng API. */
        private final List<String> allowedInputBuckets;
        private final List<String> hd8InputPrefixes;
        private final List<String> tmsInputPrefixes;
        /** S3 nhìn từ phía Doris BE (OUTFILE) — có thể khác endpoint của ứng dụng. */
        private final String dorisEndpoint;
        private final String dorisAccessKey;
        private final String dorisSecretKey;

        public S3(String endpoint, @DefaultValue("us-east-1") String region, String accessKey, String secretKey,
                @DefaultValue("true") boolean pathStyle, @DefaultValue("hddt") String exportBucket,
                @DefaultValue("export/query-result") String exportPrefix,
                @DefaultValue("export/hd8-result") String verifyResultPrefix,
                @DefaultValue("internal/jobs") String internalPrefix,
                @DefaultValue("hddt") List<String> allowedInputBuckets,
                @DefaultValue({"import/tthc/", "export/query-result/"}) List<String> hd8InputPrefixes,
                @DefaultValue("import/tms/") List<String> tmsInputPrefixes, String dorisEndpoint, String dorisAccessKey,
                String dorisSecretKey) {
            this.endpoint = endpoint;
            this.region = region;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.pathStyle = pathStyle;
            this.exportBucket = exportBucket;
            this.exportPrefix = exportPrefix;
            this.verifyResultPrefix = verifyResultPrefix;
            this.internalPrefix = internalPrefix;
            this.allowedInputBuckets = allowedInputBuckets;
            this.hd8InputPrefixes = hd8InputPrefixes;
            this.tmsInputPrefixes = tmsInputPrefixes;
            this.dorisEndpoint = dorisEndpoint;
            this.dorisAccessKey = dorisAccessKey;
            this.dorisSecretKey = dorisSecretKey;
        }

        public String endpoint() {
            return endpoint;
        }

        public String region() {
            return region;
        }

        public String accessKey() {
            return accessKey;
        }

        public String secretKey() {
            return secretKey;
        }

        public boolean pathStyle() {
            return pathStyle;
        }

        public String exportBucket() {
            return exportBucket;
        }

        public String exportPrefix() {
            return exportPrefix;
        }

        public String verifyResultPrefix() {
            return verifyResultPrefix;
        }

        public String internalPrefix() {
            return internalPrefix;
        }

        public List<String> allowedInputBuckets() {
            return allowedInputBuckets;
        }

        public List<String> hd8InputPrefixes() {
            return hd8InputPrefixes;
        }

        public List<String> tmsInputPrefixes() {
            return tmsInputPrefixes;
        }

        public String dorisEndpoint() {
            return dorisEndpoint;
        }

        public String dorisAccessKey() {
            return dorisAccessKey;
        }

        public String dorisSecretKey() {
            return dorisSecretKey;
        }

        public String effectiveDorisEndpoint() {
            return dorisEndpoint == null || Jdk8Compat.isBlank(dorisEndpoint) ? endpoint : dorisEndpoint;
        }

        public String effectiveDorisAccessKey() {
            return dorisAccessKey == null || Jdk8Compat.isBlank(dorisAccessKey) ? accessKey : dorisAccessKey;
        }

        public String effectiveDorisSecretKey() {
            return dorisSecretKey == null || Jdk8Compat.isBlank(dorisSecretKey) ? secretKey : dorisSecretKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof S3)) {
                return false;
            }
            S3 other = (S3) o;
            return Objects.equals(endpoint, other.endpoint)
                    && Objects.equals(region, other.region)
                    && Objects.equals(accessKey, other.accessKey)
                    && Objects.equals(secretKey, other.secretKey)
                    && pathStyle == other.pathStyle
                    && Objects.equals(exportBucket, other.exportBucket)
                    && Objects.equals(exportPrefix, other.exportPrefix)
                    && Objects.equals(verifyResultPrefix, other.verifyResultPrefix)
                    && Objects.equals(internalPrefix, other.internalPrefix)
                    && Objects.equals(allowedInputBuckets, other.allowedInputBuckets)
                    && Objects.equals(hd8InputPrefixes, other.hd8InputPrefixes)
                    && Objects.equals(tmsInputPrefixes, other.tmsInputPrefixes)
                    && Objects.equals(dorisEndpoint, other.dorisEndpoint)
                    && Objects.equals(dorisAccessKey, other.dorisAccessKey)
                    && Objects.equals(dorisSecretKey, other.dorisSecretKey);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(endpoint);
            result = 31 * result + Objects.hashCode(region);
            result = 31 * result + Objects.hashCode(accessKey);
            result = 31 * result + Objects.hashCode(secretKey);
            result = 31 * result + Boolean.hashCode(pathStyle);
            result = 31 * result + Objects.hashCode(exportBucket);
            result = 31 * result + Objects.hashCode(exportPrefix);
            result = 31 * result + Objects.hashCode(verifyResultPrefix);
            result = 31 * result + Objects.hashCode(internalPrefix);
            result = 31 * result + Objects.hashCode(allowedInputBuckets);
            result = 31 * result + Objects.hashCode(hd8InputPrefixes);
            result = 31 * result + Objects.hashCode(tmsInputPrefixes);
            result = 31 * result + Objects.hashCode(dorisEndpoint);
            result = 31 * result + Objects.hashCode(dorisAccessKey);
            result = 31 * result + Objects.hashCode(dorisSecretKey);
            return result;
        }

        @Override
        public String toString() {
            return "S3[endpoint=" + endpoint
                    + ", region=" + region
                    + ", accessKey=" + accessKey
                    + ", secretKey=" + secretKey
                    + ", pathStyle=" + pathStyle
                    + ", exportBucket=" + exportBucket
                    + ", exportPrefix=" + exportPrefix
                    + ", verifyResultPrefix=" + verifyResultPrefix
                    + ", internalPrefix=" + internalPrefix
                    + ", allowedInputBuckets=" + allowedInputBuckets
                    + ", hd8InputPrefixes=" + hd8InputPrefixes
                    + ", tmsInputPrefixes=" + tmsInputPrefixes
                    + ", dorisEndpoint=" + dorisEndpoint
                    + ", dorisAccessKey=" + dorisAccessKey
                    + ", dorisSecretKey=" + dorisSecretKey + "]";
        }
    }

    @ConstructorBinding
    public static final class MstStatus {
        /**
         * SQL trả (MST, MST_CU, TRANG_THAI) — mặc định view NNT_ALL_STATUS; MST_CU = MST cũ (SI-65), không có thì
         * {@code NULL AS MST_CU}. Trống ⇒ chưa có nguồn (SI-24) ⇒ HD1/HD3/HD5/HD8 trả 503.
         */
        private final String sourceSql;
        private final List<String> excludedCodes;

        public MstStatus(String sourceSql, @DefaultValue({"03", "05", "06"}) List<String> excludedCodes) {
            this.sourceSql = sourceSql;
            this.excludedCodes = excludedCodes;
        }

        public String sourceSql() {
            return sourceSql;
        }

        public List<String> excludedCodes() {
            return excludedCodes;
        }

        public boolean configured() {
            return sourceSql != null && !Jdk8Compat.isBlank(sourceSql);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MstStatus)) {
                return false;
            }
            MstStatus other = (MstStatus) o;
            return Objects.equals(sourceSql, other.sourceSql)
                    && Objects.equals(excludedCodes, other.excludedCodes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(sourceSql);
            result = 31 * result + Objects.hashCode(excludedCodes);
            return result;
        }

        @Override
        public String toString() {
            return "MstStatus[sourceSql=" + sourceSql
                    + ", excludedCodes=" + excludedCodes + "]";
        }
    }

    /** Cấu hình chung của API danh sách HD1/HD5: quy tắc mẫu số R1 (SI-46) và trả ngay khi kết quả nhỏ (SI-06). */
    public interface ListExportSettings {
        boolean formRuleEnabled();

        int realtimeRowLimit();

        Duration realtimeWait();

        Duration realtimePollInterval();

        default boolean realtimeEnabled() {
            return realtimeRowLimit() > 0 && realtimeWait() != null && !realtimeWait().isZero()
                    && !realtimeWait().isNegative();
        }
    }

    /** Cấu hình danh sách theo chiều: mua vào ⇒ {@code hddt.hd1}, bán ra ⇒ {@code hddt.hd5}. */
    public ListExportSettings listExport(Direction direction) {
        return direction == Direction.BAN_RA ? hd5 : hd1;
    }

    /** HD5 — danh sách hóa đơn bán ra (SI-47: R1 theo GiaiPhap v3.8 Bảng 5; SI-06: HD5!AA35/AA36 trả ngay &lt; 10k). */
    @ConstructorBinding
    public static final class Hd5 implements ListExportSettings {
        private final boolean formRuleEnabled;
        private final int realtimeRowLimit;
        private final Duration realtimeWait;
        private final Duration realtimePollInterval;

        public Hd5(@DefaultValue("true") boolean formRuleEnabled, @DefaultValue("10000") int realtimeRowLimit,
                @DefaultValue("PT20S") Duration realtimeWait, @DefaultValue("PT0.5S") Duration realtimePollInterval) {
            this.formRuleEnabled = formRuleEnabled;
            this.realtimeRowLimit = realtimeRowLimit;
            this.realtimeWait = realtimeWait;
            this.realtimePollInterval = realtimePollInterval;
        }

        @Override
        public boolean formRuleEnabled() {
            return formRuleEnabled;
        }

        @Override
        public int realtimeRowLimit() {
            return realtimeRowLimit;
        }

        @Override
        public Duration realtimeWait() {
            return realtimeWait;
        }

        @Override
        public Duration realtimePollInterval() {
            return realtimePollInterval;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Hd5)) {
                return false;
            }
            Hd5 other = (Hd5) o;
            return formRuleEnabled == other.formRuleEnabled
                    && realtimeRowLimit == other.realtimeRowLimit
                    && Objects.equals(realtimeWait, other.realtimeWait)
                    && Objects.equals(realtimePollInterval, other.realtimePollInterval);
        }

        @Override
        public int hashCode() {
            return Objects.hash(formRuleEnabled, realtimeRowLimit, realtimeWait, realtimePollInterval);
        }

        @Override
        public String toString() {
            return "Hd5[formRuleEnabled=" + formRuleEnabled
                    + ", realtimeRowLimit=" + realtimeRowLimit
                    + ", realtimeWait=" + realtimeWait
                    + ", realtimePollInterval=" + realtimePollInterval + "]";
        }
    }

    /** HD1 — danh sách hóa đơn mua vào (SI-46, SI-06). */
    @ConstructorBinding
    public static final class Hd1 implements ListExportSettings {
        private final boolean formRuleEnabled;
        private final int realtimeRowLimit;
        private final Duration realtimeWait;
        private final Duration realtimePollInterval;

        public Hd1(@DefaultValue("true") boolean formRuleEnabled, @DefaultValue("10000") int realtimeRowLimit,
                @DefaultValue("PT20S") Duration realtimeWait, @DefaultValue("PT0.5S") Duration realtimePollInterval) {
            this.formRuleEnabled = formRuleEnabled;
            this.realtimeRowLimit = realtimeRowLimit;
            this.realtimeWait = realtimeWait;
            this.realtimePollInterval = realtimePollInterval;
        }

        @Override
        public boolean formRuleEnabled() {
            return formRuleEnabled;
        }

        @Override
        public int realtimeRowLimit() {
            return realtimeRowLimit;
        }

        @Override
        public Duration realtimeWait() {
            return realtimeWait;
        }

        @Override
        public Duration realtimePollInterval() {
            return realtimePollInterval;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Hd1)) {
                return false;
            }
            Hd1 other = (Hd1) o;
            return formRuleEnabled == other.formRuleEnabled
                    && realtimeRowLimit == other.realtimeRowLimit
                    && Objects.equals(realtimeWait, other.realtimeWait)
                    && Objects.equals(realtimePollInterval, other.realtimePollInterval);
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(formRuleEnabled);
            result = 31 * result + Integer.hashCode(realtimeRowLimit);
            result = 31 * result + Objects.hashCode(realtimeWait);
            result = 31 * result + Objects.hashCode(realtimePollInterval);
            return result;
        }

        @Override
        public String toString() {
            return "Hd1[formRuleEnabled=" + formRuleEnabled
                    + ", realtimeRowLimit=" + realtimeRowLimit
                    + ", realtimeWait=" + realtimeWait
                    + ", realtimePollInterval=" + realtimePollInterval + "]";
        }
    }

    @ConstructorBinding
    public static final class Hd7 {
        private final int maxMonths;
        /** R1 mẫu số (GiaiPhap v3.8 Bảng 5) cho cả mua vào và bán ra — 04/11, cùng biểu thức với HD1/HD5. */
        private final boolean formRuleEnabled;
        /** [23]/[24]: mã tsuat được cộng vào mua vào (GiaiPhap v3.8 Bảng 6: 0%, 5%, 10%; không KCT/KKKNT/8%/KHAC). */
        private final List<String> mvaoTsuat;
        /** Trường `bra` → danh sách mã tsuat; danh sách rỗng ⇒ trường trả NULL (chưa có nguồn). */
        private final Map<String, List<String>> braTsuat;
        /** Mã có số liệu nhưng không thuộc chỉ tiêu nào (KHAC...): IGNORE = bỏ qua + cảnh báo (chốt 01/11). */
        private final UnassignedCodePolicy unassignedTsuatPolicy;
        /**
         * HĐ không có THTTLTSUAT và không có dòng hàng có thuế suất ⇒ thử các mức này theo tỷ lệ TGTTHUE/TGTCTHUE
         * header (04/11). Rỗng = tắt (HĐ vào nhóm không xác định). Không suy 0% (không phân biệt được với KCT/KKKNT).
         */
        private final List<String> headerRateInference;
        /** Sai số chấp nhận khi suy thuế suất: |TGTTHUE − TGTCTHUE × r| ≤ max(VND, |TGTCTHUE| × ratio). */
        private final BigDecimal headerRateToleranceVnd;
        private final BigDecimal headerRateToleranceRatio;
        /**
         * Cách xác định kỳ (05/11): NGAY_LAP = GiaiPhap v3.8 R3/R4 — kỳ theo ngày lập, HĐ thay thế/điều chỉnh theo ngày
         * lập HĐ gốc (TDLHDGOC), cả bán ra và mua vào; KY_DANH_SACH = cột KY của HD_MST_LIST (bán ra TDLAP, mua vào
         * NKY→NCMA→TDLAP như HD1, SI-42).
         */
        private final PeriodBasis periodBasis;
        /** R7 (v3.8): nhân tỷ giá TGIA khi DVTTE ≠ VND và TGIA > 0. */
        private final boolean currencyConversionEnabled;
        /** D3 (v3.8): HĐ điều chỉnh (TCHAT = 3) thiếu THTTLTSUAT đóng góp 0 (không lấy dòng hàng/header). */
        private final boolean adjustmentWithoutRatesZero;
        /**
         * HĐ thiếu THTTLTSUAT, dòng hàng chỉ có 1 thuế suất (06/11): LINES = v3.8 R6/`v_stg_ts` — tổng dòng hàng, dòng
         * không có số thì lấy header; HEADER = TGTCTHUE/TGTTHUE header (cách 01/11–05/11), header rỗng thì dòng hàng.
         */
        private final SingleRateSource singleRateSource;

        public Hd7(@DefaultValue("12") int maxMonths, @DefaultValue("true") boolean formRuleEnabled,
                @DefaultValue({"0%", "5%", "10%"}) List<String> mvaoTsuat, Map<String, List<String>> braTsuat,
                @DefaultValue("IGNORE") UnassignedCodePolicy unassignedTsuatPolicy,
                @DefaultValue({"10%", "8%", "5%"}) List<String> headerRateInference,
                @DefaultValue("1") BigDecimal headerRateToleranceVnd,
                @DefaultValue("0.0001") BigDecimal headerRateToleranceRatio,
                @DefaultValue("NGAY_LAP") PeriodBasis periodBasis,
                @DefaultValue("true") boolean currencyConversionEnabled,
                @DefaultValue("true") boolean adjustmentWithoutRatesZero,
                @DefaultValue("LINES") SingleRateSource singleRateSource) {
            this.singleRateSource = singleRateSource == null ? SingleRateSource.LINES : singleRateSource;
            this.periodBasis = periodBasis;
            this.currencyConversionEnabled = currencyConversionEnabled;
            this.adjustmentWithoutRatesZero = adjustmentWithoutRatesZero;
            this.maxMonths = maxMonths;
            this.formRuleEnabled = formRuleEnabled;
            this.mvaoTsuat = mvaoTsuat == null ? Jdk8Compat.listOf() : Jdk8Compat.listCopyOf(mvaoTsuat);
            this.braTsuat = braTsuat;
            this.unassignedTsuatPolicy = unassignedTsuatPolicy;
            this.headerRateInference = headerRateInference == null ? Jdk8Compat.listOf()
                    : Jdk8Compat.listCopyOf(headerRateInference);
            this.headerRateToleranceVnd = headerRateToleranceVnd;
            this.headerRateToleranceRatio = headerRateToleranceRatio;
        }

        public int maxMonths() {
            return maxMonths;
        }

        public boolean formRuleEnabled() {
            return formRuleEnabled;
        }

        public List<String> mvaoTsuat() {
            return mvaoTsuat;
        }

        public Map<String, List<String>> braTsuat() {
            return braTsuat;
        }

        public UnassignedCodePolicy unassignedTsuatPolicy() {
            return unassignedTsuatPolicy;
        }

        public List<String> headerRateInference() {
            return headerRateInference;
        }

        public BigDecimal headerRateToleranceVnd() {
            return headerRateToleranceVnd;
        }

        public BigDecimal headerRateToleranceRatio() {
            return headerRateToleranceRatio;
        }

        public PeriodBasis periodBasis() {
            return periodBasis;
        }

        public boolean currencyConversionEnabled() {
            return currencyConversionEnabled;
        }

        public boolean adjustmentWithoutRatesZero() {
            return adjustmentWithoutRatesZero;
        }

        public SingleRateSource singleRateSource() {
            return singleRateSource;
        }

        /** Cách xác định kỳ của HĐ cho HD7. */
        public enum PeriodBasis { NGAY_LAP, KY_DANH_SACH }

        /** Nguồn số tiền của HĐ thiếu THTTLTSUAT có 1 thuế suất dòng hàng. */
        public enum SingleRateSource { LINES, HEADER }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Hd7)) {
                return false;
            }
            Hd7 other = (Hd7) o;
            return maxMonths == other.maxMonths
                    && formRuleEnabled == other.formRuleEnabled
                    && Objects.equals(mvaoTsuat, other.mvaoTsuat)
                    && Objects.equals(braTsuat, other.braTsuat)
                    && Objects.equals(unassignedTsuatPolicy, other.unassignedTsuatPolicy)
                    && Objects.equals(headerRateInference, other.headerRateInference)
                    && Objects.equals(headerRateToleranceVnd, other.headerRateToleranceVnd)
                    && Objects.equals(headerRateToleranceRatio, other.headerRateToleranceRatio)
                    && periodBasis == other.periodBasis
                    && currencyConversionEnabled == other.currencyConversionEnabled
                    && adjustmentWithoutRatesZero == other.adjustmentWithoutRatesZero
                    && singleRateSource == other.singleRateSource;
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxMonths, formRuleEnabled, mvaoTsuat, braTsuat, unassignedTsuatPolicy,
                    headerRateInference, headerRateToleranceVnd, headerRateToleranceRatio, periodBasis,
                    currencyConversionEnabled, adjustmentWithoutRatesZero, singleRateSource);
        }

        @Override
        public String toString() {
            return "Hd7[maxMonths=" + maxMonths
                    + ", formRuleEnabled=" + formRuleEnabled
                    + ", mvaoTsuat=" + mvaoTsuat
                    + ", braTsuat=" + braTsuat
                    + ", unassignedTsuatPolicy=" + unassignedTsuatPolicy
                    + ", headerRateInference=" + headerRateInference
                    + ", headerRateToleranceVnd=" + headerRateToleranceVnd
                    + ", headerRateToleranceRatio=" + headerRateToleranceRatio
                    + ", periodBasis=" + periodBasis
                    + ", currencyConversionEnabled=" + currencyConversionEnabled
                    + ", adjustmentWithoutRatesZero=" + adjustmentWithoutRatesZero
                    + ", singleRateSource=" + singleRateSource + "]";
        }
    }

    @ConstructorBinding
    public static final class Hd8 {
        /** SI-13: phải true (nghiệp vụ đã duyệt quy tắc so sánh) thì API HD8 mới nhận yêu cầu. */
        private final boolean rulesConfirmed;
        private final int batchSize;
        private final long maxRows;
        private final int maxObjects;
        private final long maxTotalBytes;
        private final int maxDays;
        private final List<String> compareFields;
        private final BigDecimal moneyTolerance;
        private final int maxReportedErrors;
        private final boolean candidateIndex;
        private final int indexMaxCandidatesPerKey;

        /**
         * @param candidateIndex DB-P1-02 (TD-API-35): true ⇒ một truy vấn ứng viên HD_MST_LIST mỗi job + tra theo khóa literal
         *                       mỗi lô; false ⇒ cách cũ (mỗi lô một truy vấn LIST ⨝ HDR không cắt KY) — chỉ để dự phòng.
         * @param indexMaxCandidatesPerKey chỉ dựng chỉ mục khi số ứng viên của người mua trong [tngay, dngay] ≤ số dòng file × giá
         *                       trị này (đếm trước bằng COUNT phía Doris); vượt ⇒ cách cũ — file nhỏ của người mua rất lớn
         *                       không phải kéo hết ứng viên về (DEV 07/11: 2,07 triệu ứng viên cho 2.222 khóa: 82 s vs 5,6 s).
         */
        public Hd8(@DefaultValue("false") boolean rulesConfirmed, @DefaultValue("500") int batchSize,
                @DefaultValue("2000000") long maxRows, @DefaultValue("100") int maxObjects,
                @DefaultValue("2147483648") long maxTotalBytes, @DefaultValue("366") int maxDays,
                List<String> compareFields, @DefaultValue("0") BigDecimal moneyTolerance,
                @DefaultValue("200") int maxReportedErrors, @DefaultValue("true") boolean candidateIndex,
                @DefaultValue("20") int indexMaxCandidatesPerKey) {
            this.rulesConfirmed = rulesConfirmed;
            this.batchSize = batchSize;
            this.maxRows = maxRows;
            this.maxObjects = maxObjects;
            this.maxTotalBytes = maxTotalBytes;
            this.maxDays = maxDays;
            this.compareFields = compareFields;
            this.moneyTolerance = moneyTolerance;
            this.maxReportedErrors = maxReportedErrors;
            this.candidateIndex = candidateIndex;
            this.indexMaxCandidatesPerKey = indexMaxCandidatesPerKey;
        }

        public boolean rulesConfirmed() {
            return rulesConfirmed;
        }

        public int batchSize() {
            return batchSize;
        }

        public long maxRows() {
            return maxRows;
        }

        public int maxObjects() {
            return maxObjects;
        }

        public long maxTotalBytes() {
            return maxTotalBytes;
        }

        public int maxDays() {
            return maxDays;
        }

        public List<String> compareFields() {
            return compareFields;
        }

        public BigDecimal moneyTolerance() {
            return moneyTolerance;
        }

        public int maxReportedErrors() {
            return maxReportedErrors;
        }

        public boolean candidateIndex() {
            return candidateIndex;
        }

        public int indexMaxCandidatesPerKey() {
            return indexMaxCandidatesPerKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Hd8)) {
                return false;
            }
            Hd8 other = (Hd8) o;
            return rulesConfirmed == other.rulesConfirmed
                    && batchSize == other.batchSize
                    && maxRows == other.maxRows
                    && maxObjects == other.maxObjects
                    && maxTotalBytes == other.maxTotalBytes
                    && maxDays == other.maxDays
                    && Objects.equals(compareFields, other.compareFields)
                    && Objects.equals(moneyTolerance, other.moneyTolerance)
                    && maxReportedErrors == other.maxReportedErrors
                    && candidateIndex == other.candidateIndex
                    && indexMaxCandidatesPerKey == other.indexMaxCandidatesPerKey;
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(rulesConfirmed);
            result = 31 * result + Integer.hashCode(batchSize);
            result = 31 * result + Long.hashCode(maxRows);
            result = 31 * result + Integer.hashCode(maxObjects);
            result = 31 * result + Long.hashCode(maxTotalBytes);
            result = 31 * result + Integer.hashCode(maxDays);
            result = 31 * result + Objects.hashCode(compareFields);
            result = 31 * result + Objects.hashCode(moneyTolerance);
            result = 31 * result + Integer.hashCode(maxReportedErrors);
            result = 31 * result + Boolean.hashCode(candidateIndex);
            result = 31 * result + Integer.hashCode(indexMaxCandidatesPerKey);
            return result;
        }

        @Override
        public String toString() {
            return "Hd8[rulesConfirmed=" + rulesConfirmed
                    + ", batchSize=" + batchSize
                    + ", maxRows=" + maxRows
                    + ", maxObjects=" + maxObjects
                    + ", maxTotalBytes=" + maxTotalBytes
                    + ", maxDays=" + maxDays
                    + ", compareFields=" + compareFields
                    + ", moneyTolerance=" + moneyTolerance
                    + ", maxReportedErrors=" + maxReportedErrors
                    + ", candidateIndex=" + candidateIndex
                    + ", indexMaxCandidatesPerKey=" + indexMaxCandidatesPerKey + "]";
        }
    }

    @ConstructorBinding
    public static final class Tms {
        private final ApplyMode applyMode;
        private final int batchSize;
        private final long maxRows;
        private final int maxObjects;
        private final long maxTotalBytes;
        /** Bảng sổ hoàn trong database Doris {@code hddt.doris.database} (sql/doris/02_hd_so_hoan.sql). */
        private final String ledgerTable;
        private final int ledgerChunkSize;

        public Tms(@DefaultValue("LEDGER") ApplyMode applyMode, @DefaultValue("500") int batchSize,
                @DefaultValue("1000000") long maxRows, @DefaultValue("100") int maxObjects,
                @DefaultValue("1073741824") long maxTotalBytes, @DefaultValue("HD_SO_HOAN") String ledgerTable,
                @DefaultValue("1000") int ledgerChunkSize) {
            this.applyMode = applyMode;
            this.batchSize = batchSize;
            this.maxRows = maxRows;
            this.maxObjects = maxObjects;
            this.maxTotalBytes = maxTotalBytes;
            this.ledgerTable = ledgerTable;
            this.ledgerChunkSize = ledgerChunkSize;
        }

        public ApplyMode applyMode() {
            return applyMode;
        }

        public int batchSize() {
            return batchSize;
        }

        public long maxRows() {
            return maxRows;
        }

        public int maxObjects() {
            return maxObjects;
        }

        public long maxTotalBytes() {
            return maxTotalBytes;
        }

        public String ledgerTable() {
            return ledgerTable;
        }

        public int ledgerChunkSize() {
            return ledgerChunkSize;
        }

        /**
         * LEDGER: kiểm tra đủ ⇒ ghi sổ hoàn Doris (mặc định, 01/11). BLOCKED: kiểm tra xong ⇒ LOI, không ghi.
         * VALIDATE_ONLY: chỉ kiểm tra (DEV/TEST, cấm ở prod).
         */
        public enum ApplyMode { LEDGER, BLOCKED, VALIDATE_ONLY }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Tms)) {
                return false;
            }
            Tms other = (Tms) o;
            return Objects.equals(applyMode, other.applyMode)
                    && batchSize == other.batchSize
                    && maxRows == other.maxRows
                    && maxObjects == other.maxObjects
                    && maxTotalBytes == other.maxTotalBytes
                    && Objects.equals(ledgerTable, other.ledgerTable)
                    && ledgerChunkSize == other.ledgerChunkSize;
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(applyMode);
            result = 31 * result + Integer.hashCode(batchSize);
            result = 31 * result + Long.hashCode(maxRows);
            result = 31 * result + Integer.hashCode(maxObjects);
            result = 31 * result + Long.hashCode(maxTotalBytes);
            result = 31 * result + Objects.hashCode(ledgerTable);
            result = 31 * result + Integer.hashCode(ledgerChunkSize);
            return result;
        }

        @Override
        public String toString() {
            return "Tms[applyMode=" + applyMode
                    + ", batchSize=" + batchSize
                    + ", maxRows=" + maxRows
                    + ", maxObjects=" + maxObjects
                    + ", maxTotalBytes=" + maxTotalBytes
                    + ", ledgerTable=" + ledgerTable
                    + ", ledgerChunkSize=" + ledgerChunkSize + "]";
        }
    }

    @ConstructorBinding
    public static final class Export {
        private final Engine engine;
        private final long maxFileBytes;
        private final int retentionDays;
        private final boolean parallelOutfile;
        /** Kết quả ≤ ngưỡng này được đọc lại để đếm bản ghi CSV trước khi công bố. */
        private final long verifyRecordCountMaxBytes;
        private final String successFileName;

        public Export(@DefaultValue("OUTFILE") Engine engine, @DefaultValue("1073741824") long maxFileBytes,
                @DefaultValue("7") int retentionDays, @DefaultValue("true") boolean parallelOutfile,
                @DefaultValue("268435456") long verifyRecordCountMaxBytes,
                @DefaultValue("_SUCCESS") String successFileName) {
            this.engine = engine;
            this.maxFileBytes = maxFileBytes;
            this.retentionDays = retentionDays;
            this.parallelOutfile = parallelOutfile;
            this.verifyRecordCountMaxBytes = verifyRecordCountMaxBytes;
            this.successFileName = successFileName;
        }

        public Engine engine() {
            return engine;
        }

        public long maxFileBytes() {
            return maxFileBytes;
        }

        public int retentionDays() {
            return retentionDays;
        }

        public boolean parallelOutfile() {
            return parallelOutfile;
        }

        public long verifyRecordCountMaxBytes() {
            return verifyRecordCountMaxBytes;
        }

        public String successFileName() {
            return successFileName;
        }

        public enum Engine { OUTFILE, JDBC_STREAM }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Export)) {
                return false;
            }
            Export other = (Export) o;
            return Objects.equals(engine, other.engine)
                    && maxFileBytes == other.maxFileBytes
                    && retentionDays == other.retentionDays
                    && parallelOutfile == other.parallelOutfile
                    && verifyRecordCountMaxBytes == other.verifyRecordCountMaxBytes
                    && Objects.equals(successFileName, other.successFileName);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(engine);
            result = 31 * result + Long.hashCode(maxFileBytes);
            result = 31 * result + Integer.hashCode(retentionDays);
            result = 31 * result + Boolean.hashCode(parallelOutfile);
            result = 31 * result + Long.hashCode(verifyRecordCountMaxBytes);
            result = 31 * result + Objects.hashCode(successFileName);
            return result;
        }

        @Override
        public String toString() {
            return "Export[engine=" + engine
                    + ", maxFileBytes=" + maxFileBytes
                    + ", retentionDays=" + retentionDays
                    + ", parallelOutfile=" + parallelOutfile
                    + ", verifyRecordCountMaxBytes=" + verifyRecordCountMaxBytes
                    + ", successFileName=" + successFileName + "]";
        }
    }

    @ConstructorBinding
    public static final class Worker {
        private final boolean enabled;
        private final int concurrency;
        private final Duration lease;
        private final Duration pollInterval;
        private final int maxAttempts;
        private final Duration retryBackoff;
        private final Duration maxRetryBackoff;
        private final Duration shutdownGrace;
        /** Giới hạn job chạy đồng thời toàn cụm theo loại (soft limit, đếm trong DB). */
        private final Map<JobType, Integer> maxRunningPerType;

        public Worker(@DefaultValue("true") boolean enabled, @DefaultValue("4") int concurrency,
                @DefaultValue("PT2M") Duration lease, @DefaultValue("PT1S") Duration pollInterval,
                @DefaultValue("3") int maxAttempts, @DefaultValue("PT30S") Duration retryBackoff,
                @DefaultValue("PT10M") Duration maxRetryBackoff, @DefaultValue("PT5M") Duration shutdownGrace,
                Map<JobType, Integer> maxRunningPerType) {
            this.enabled = enabled;
            this.concurrency = concurrency;
            this.lease = lease;
            this.pollInterval = pollInterval;
            this.maxAttempts = maxAttempts;
            this.retryBackoff = retryBackoff;
            this.maxRetryBackoff = maxRetryBackoff;
            this.shutdownGrace = shutdownGrace;
            this.maxRunningPerType = maxRunningPerType;
        }

        public boolean enabled() {
            return enabled;
        }

        public int concurrency() {
            return concurrency;
        }

        public Duration lease() {
            return lease;
        }

        public Duration pollInterval() {
            return pollInterval;
        }

        public int maxAttempts() {
            return maxAttempts;
        }

        public Duration retryBackoff() {
            return retryBackoff;
        }

        public Duration maxRetryBackoff() {
            return maxRetryBackoff;
        }

        public Duration shutdownGrace() {
            return shutdownGrace;
        }

        public Map<JobType, Integer> maxRunningPerType() {
            return maxRunningPerType;
        }

        public int maxRunning(JobType type) {
            Integer v = maxRunningPerType == null ? null : maxRunningPerType.get(type);
            return v == null ? concurrency : v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Worker)) {
                return false;
            }
            Worker other = (Worker) o;
            return enabled == other.enabled
                    && concurrency == other.concurrency
                    && Objects.equals(lease, other.lease)
                    && Objects.equals(pollInterval, other.pollInterval)
                    && maxAttempts == other.maxAttempts
                    && Objects.equals(retryBackoff, other.retryBackoff)
                    && Objects.equals(maxRetryBackoff, other.maxRetryBackoff)
                    && Objects.equals(shutdownGrace, other.shutdownGrace)
                    && Objects.equals(maxRunningPerType, other.maxRunningPerType);
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(enabled);
            result = 31 * result + Integer.hashCode(concurrency);
            result = 31 * result + Objects.hashCode(lease);
            result = 31 * result + Objects.hashCode(pollInterval);
            result = 31 * result + Integer.hashCode(maxAttempts);
            result = 31 * result + Objects.hashCode(retryBackoff);
            result = 31 * result + Objects.hashCode(maxRetryBackoff);
            result = 31 * result + Objects.hashCode(shutdownGrace);
            result = 31 * result + Objects.hashCode(maxRunningPerType);
            return result;
        }

        @Override
        public String toString() {
            return "Worker[enabled=" + enabled
                    + ", concurrency=" + concurrency
                    + ", lease=" + lease
                    + ", pollInterval=" + pollInterval
                    + ", maxAttempts=" + maxAttempts
                    + ", retryBackoff=" + retryBackoff
                    + ", maxRetryBackoff=" + maxRetryBackoff
                    + ", shutdownGrace=" + shutdownGrace
                    + ", maxRunningPerType=" + maxRunningPerType + "]";
        }
    }

    /**
     * Xác thực/phân quyền /hthue/** (TD-API-40, 09/11: bỏ X-Client-Id/X-Client-Secret — chỉ gọi nội bộ qua trục). Bên gọi
     * xác định bằng header {@code x-partner} → {@link #partnerClients()} → {@link Client} (quyền, phạm vi MST, rate-limit);
     * có khóa ESB thì x-partner phải kèm chữ ký {@code x-hmac} đúng; không khóa ⇒ không xác thực.
     */
    @ConstructorBinding
    public static final class Security {
        /** Hồ sơ quyền theo bên gọi (không còn secret). */
        private final List<Client> clients;
        /** Tùy chọn: header bí mật do APIC gắn thêm; trống ⇒ không kiểm. */
        private final String gatewayTokenHeader;
        private final String gatewayTokenSha256;
        /** Tùy chọn: chữ ký HMAC do ESB gắn khi gọi vào (như hddt-tthc-sharing, TD-API-40); không khai báo khóa ⇒ không kiểm. */
        private final Esb esb;
        /** Mã đối tác (x-partner, không phân biệt hoa thường) → client id. */
        private final Map<String, String> partnerClients;
        /** Client dùng khi KHÔNG có khóa ESB và request không gửi x-partner; trống ⇒ thiếu x-partner bị 401. */
        private final String defaultClient;

        public Security(List<Client> clients, String gatewayTokenHeader, String gatewayTokenSha256, @DefaultValue Esb esb,
                Map<String, String> partnerClients, String defaultClient) {
            this.clients = clients;
            this.gatewayTokenHeader = gatewayTokenHeader;
            this.gatewayTokenSha256 = gatewayTokenSha256;
            this.esb = esb;
            this.partnerClients = partnerClients == null ? Jdk8Compat.mapOf() : partnerClients;
            this.defaultClient = defaultClient;
        }

        public Map<String, String> partnerClients() {
            return partnerClients;
        }

        public String defaultClient() {
            return defaultClient;
        }

        /** Cấu hình ESB; {@code null} (vd test dựng tay) ⇒ coi như tắt. */
        public Esb esb() {
            return esb;
        }

        /**
         * ESB gọi vào (TD-API-40) — cùng cơ chế {@code HmacFilter} của hddt-tthc-sharing: header {@code x-partner} = mã đối
         * tác, {@code x-hmac} = HMAC-SHA256 (hex) của chuỗi {@code yyyyMMdd.<partner>} với khóa dùng chung, ngày theo giờ VN.
         * Có khóa ⇒ chữ ký bắt buộc và là cách xác thực duy nhất (không còn X-Client-Id/X-Client-Secret).
         */
        @ConstructorBinding
        public static final class Esb {
            /** Khóa HMAC dùng chung với ESB (tương ứng {@code tthc.hmac.key} của tthc-sharing). Trống ⇒ tắt. */
            private final String hmacKey;
            private final String partnerHeader;
            private final String hmacHeader;
            /** Mã đối tác được nhận (so không phân biệt hoa thường); trống ⇒ mọi đối tác có chữ ký đúng. */
            private final List<String> allowedPartners;
            /** Múi giờ tính yyyyMMdd — phải trùng ESB (tthc-sharing dùng giờ VN); JVM của module có thể chạy UTC. */
            private final String zone;
            /** Lệch đồng hồ cho phép quanh nửa đêm: chấp nhận cả ngày của (now ± clockSkew). */
            private final Duration clockSkew;

            public Esb(String hmacKey, @DefaultValue("x-partner") String partnerHeader,
                       @DefaultValue("x-hmac") String hmacHeader, List<String> allowedPartners,
                       @DefaultValue("Asia/Ho_Chi_Minh") String zone, @DefaultValue("PT5M") Duration clockSkew) {
                this.hmacKey = hmacKey;
                this.partnerHeader = partnerHeader;
                this.hmacHeader = hmacHeader;
                this.allowedPartners = allowedPartners;
                this.zone = zone;
                this.clockSkew = clockSkew;
            }

            public boolean enabled() {
                return hmacKey != null && !Jdk8Compat.isBlank(hmacKey);
            }

            public String hmacKey() {
                return hmacKey;
            }

            public String partnerHeader() {
                return partnerHeader;
            }

            public String hmacHeader() {
                return hmacHeader;
            }

            public List<String> allowedPartners() {
                return allowedPartners;
            }

            public String zone() {
                return zone;
            }

            public Duration clockSkew() {
                return clockSkew;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof Esb)) {
                    return false;
                }
                Esb other = (Esb) o;
                return Objects.equals(hmacKey, other.hmacKey)
                        && Objects.equals(partnerHeader, other.partnerHeader)
                        && Objects.equals(hmacHeader, other.hmacHeader)
                        && Objects.equals(allowedPartners, other.allowedPartners)
                        && Objects.equals(zone, other.zone)
                        && Objects.equals(clockSkew, other.clockSkew);
            }

            @Override
            public int hashCode() {
                return Objects.hash(hmacKey, partnerHeader, hmacHeader, allowedPartners, zone, clockSkew);
            }

            /** Không in khóa HMAC. */
            @Override
            public String toString() {
                return "Esb[enabled=" + enabled()
                        + ", partnerHeader=" + partnerHeader
                        + ", hmacHeader=" + hmacHeader
                        + ", allowedPartners=" + allowedPartners
                        + ", zone=" + zone
                        + ", clockSkew=" + clockSkew + "]";
            }
        }

        public List<Client> clients() {
            return clients;
        }

        public String gatewayTokenHeader() {
            return gatewayTokenHeader;
        }

        public String gatewayTokenSha256() {
            return gatewayTokenSha256;
        }

        @ConstructorBinding
        public static final class Client {
            private final String id;
            private final Set<ApiPermission> permissions;
            private final boolean allMst;
            private final Set<String> allowedMsts;
            private final double ratePerSecond;
            private final int burst;

            public Client(String id, Set<ApiPermission> permissions,
                    @DefaultValue("true") boolean allMst, Set<String> allowedMsts,
                    @DefaultValue("50") double ratePerSecond, @DefaultValue("100") int burst) {
                this.id = id;
                this.permissions = permissions;
                this.allMst = allMst;
                this.allowedMsts = allowedMsts;
                this.ratePerSecond = ratePerSecond;
                this.burst = burst;
            }

            public String id() {
                return id;
            }

            public Set<ApiPermission> permissions() {
                return permissions;
            }

            public boolean allMst() {
                return allMst;
            }

            public Set<String> allowedMsts() {
                return allowedMsts;
            }

            public double ratePerSecond() {
                return ratePerSecond;
            }

            public int burst() {
                return burst;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof Client)) {
                    return false;
                }
                Client other = (Client) o;
                return Objects.equals(id, other.id)
                        && Objects.equals(permissions, other.permissions)
                        && allMst == other.allMst
                        && Objects.equals(allowedMsts, other.allowedMsts)
                        && Double.compare(ratePerSecond, other.ratePerSecond) == 0
                        && burst == other.burst;
            }

            @Override
            public int hashCode() {
                int result = Objects.hashCode(id);
                result = 31 * result + Objects.hashCode(permissions);
                result = 31 * result + Boolean.hashCode(allMst);
                result = 31 * result + Objects.hashCode(allowedMsts);
                result = 31 * result + Double.hashCode(ratePerSecond);
                result = 31 * result + Integer.hashCode(burst);
                return result;
            }

            @Override
            public String toString() {
                return "Client[id=" + id
                        + ", permissions=" + permissions
                        + ", allMst=" + allMst
                        + ", allowedMsts=" + allowedMsts
                        + ", ratePerSecond=" + ratePerSecond
                        + ", burst=" + burst + "]";
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Security)) {
                return false;
            }
            Security other = (Security) o;
            return Objects.equals(clients, other.clients)
                    && Objects.equals(gatewayTokenHeader, other.gatewayTokenHeader)
                    && Objects.equals(gatewayTokenSha256, other.gatewayTokenSha256)
                    && Objects.equals(esb, other.esb)
                    && Objects.equals(partnerClients, other.partnerClients)
                    && Objects.equals(defaultClient, other.defaultClient);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(clients);
            result = 31 * result + Objects.hashCode(gatewayTokenHeader);
            result = 31 * result + Objects.hashCode(gatewayTokenSha256);
            result = 31 * result + Objects.hashCode(esb);
            result = 31 * result + Objects.hashCode(partnerClients);
            result = 31 * result + Objects.hashCode(defaultClient);
            return result;
        }

        @Override
        public String toString() {
            return "Security[clients=" + clients
                    + ", gatewayTokenHeader=" + gatewayTokenHeader
                    + ", gatewayTokenSha256=" + gatewayTokenSha256
                    + ", esb=" + esb
                    + ", partnerClients=" + partnerClients
                    + ", defaultClient=" + defaultClient + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HddtProperties)) {
            return false;
        }
        HddtProperties other = (HddtProperties) o;
        return Objects.equals(api, other.api)
                && Objects.equals(doris, other.doris)
                && Objects.equals(dse, other.dse)
                && Objects.equals(s3, other.s3)
                && Objects.equals(mstStatus, other.mstStatus)
                && Objects.equals(hd1, other.hd1)
                && Objects.equals(hd5, other.hd5)
                && Objects.equals(hd7, other.hd7)
                && Objects.equals(hd8, other.hd8)
                && Objects.equals(tms, other.tms)
                && Objects.equals(export, other.export)
                && Objects.equals(worker, other.worker)
                && Objects.equals(security, other.security)
                && Objects.equals(jobCodes, other.jobCodes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(api);
        result = 31 * result + Objects.hashCode(doris);
        result = 31 * result + Objects.hashCode(dse);
        result = 31 * result + Objects.hashCode(s3);
        result = 31 * result + Objects.hashCode(mstStatus);
        result = 31 * result + Objects.hashCode(hd1);
        result = 31 * result + Objects.hashCode(hd5);
        result = 31 * result + Objects.hashCode(hd7);
        result = 31 * result + Objects.hashCode(hd8);
        result = 31 * result + Objects.hashCode(tms);
        result = 31 * result + Objects.hashCode(export);
        result = 31 * result + Objects.hashCode(worker);
        result = 31 * result + Objects.hashCode(security);
        result = 31 * result + Objects.hashCode(jobCodes);
        return result;
    }

    @Override
    public String toString() {
        return "HddtProperties[api=" + api
                + ", doris=" + doris
                + ", dse=" + dse
                + ", s3=" + s3
                + ", mstStatus=" + mstStatus
                + ", hd1=" + hd1
                + ", hd5=" + hd5
                + ", hd7=" + hd7
                + ", hd8=" + hd8
                + ", tms=" + tms
                + ", export=" + export
                + ", worker=" + worker
                + ", security=" + security
                + ", jobCodes=" + jobCodes + "]";
    }
}
