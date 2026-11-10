package vn.teca.hddt.dataapi.adapter.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** DTO của 7 API — tên trường đúng bảng đặc tả (xem docs/spec/API_TRACEABILITY.md). */
public final class ApiDtos {
    public static final String MST_REGEX = "^(\\d{10}(-\\d{3})?|\\d{12})$";
    public static final String MONTH_REGEX = "^(0[1-9]|1[0-2])/\\d{4}$";
    public static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";

    private ApiDtos() {
    }

    /** HD1 body (HD1!C14–C18). */
    public static final class PurchaseListRequest {
        @NotBlank
        @Pattern(regexp = MST_REGEX, message = "nmmst không đúng định dạng MST")
        @JsonProperty("nmmst")
        private final String nmmst;
        @NotBlank
        @Pattern(regexp = MONTH_REGEX, message = "tthang phải có dạng MM/YYYY")
        @JsonProperty("tthang")
        private final String tthang;
        @NotBlank
        @Pattern(regexp = MONTH_REGEX, message = "dthang phải có dạng MM/YYYY")
        @JsonProperty("dthang")
        private final String dthang;
        @NotNull(message = "lbttmsthue bắt buộc")
        @JsonProperty("lbttmsthue")
        private final Boolean lbttmsthue;
        @NotNull(message = "lbbhphap bắt buộc")
        @JsonProperty("lbbhphap")
        private final Boolean lbbhphap;

        @JsonCreator
        public PurchaseListRequest(@JsonProperty("nmmst") String nmmst, @JsonProperty("tthang") String tthang,
                @JsonProperty("dthang") String dthang, @JsonProperty("lbttmsthue") Boolean lbttmsthue,
                @JsonProperty("lbbhphap") Boolean lbbhphap) {
            this.nmmst = nmmst;
            this.tthang = tthang;
            this.dthang = dthang;
            this.lbttmsthue = lbttmsthue;
            this.lbbhphap = lbbhphap;
        }

        public String nmmst() {
            return nmmst;
        }

        public String tthang() {
            return tthang;
        }

        public String dthang() {
            return dthang;
        }

        public Boolean lbttmsthue() {
            return lbttmsthue;
        }

        public Boolean lbbhphap() {
            return lbbhphap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PurchaseListRequest)) {
                return false;
            }
            PurchaseListRequest other = (PurchaseListRequest) o;
            return Objects.equals(nmmst, other.nmmst)
                    && Objects.equals(tthang, other.tthang)
                    && Objects.equals(dthang, other.dthang)
                    && Objects.equals(lbttmsthue, other.lbttmsthue)
                    && Objects.equals(lbbhphap, other.lbbhphap);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(nmmst);
            result = 31 * result + Objects.hashCode(tthang);
            result = 31 * result + Objects.hashCode(dthang);
            result = 31 * result + Objects.hashCode(lbttmsthue);
            result = 31 * result + Objects.hashCode(lbbhphap);
            return result;
        }

        @Override
        public String toString() {
            return "PurchaseListRequest[nmmst=" + nmmst
                    + ", tthang=" + tthang
                    + ", dthang=" + dthang
                    + ", lbttmsthue=" + lbttmsthue
                    + ", lbbhphap=" + lbbhphap + "]";
        }
    }

    /** HD5 body (HD5!C14–C18). */
    public static final class SalesListRequest {
        @NotBlank
        @Pattern(regexp = MST_REGEX, message = "nbmst không đúng định dạng MST")
        @JsonProperty("nbmst")
        private final String nbmst;
        @NotBlank
        @Pattern(regexp = MONTH_REGEX, message = "tthang phải có dạng MM/YYYY")
        @JsonProperty("tthang")
        private final String tthang;
        @NotBlank
        @Pattern(regexp = MONTH_REGEX, message = "dthang phải có dạng MM/YYYY")
        @JsonProperty("dthang")
        private final String dthang;
        @NotNull(message = "lbttmsthue bắt buộc")
        @JsonProperty("lbttmsthue")
        private final Boolean lbttmsthue;
        @NotNull(message = "lbbhphap bắt buộc")
        @JsonProperty("lbbhphap")
        private final Boolean lbbhphap;

        @JsonCreator
        public SalesListRequest(@JsonProperty("nbmst") String nbmst, @JsonProperty("tthang") String tthang,
                @JsonProperty("dthang") String dthang, @JsonProperty("lbttmsthue") Boolean lbttmsthue,
                @JsonProperty("lbbhphap") Boolean lbbhphap) {
            this.nbmst = nbmst;
            this.tthang = tthang;
            this.dthang = dthang;
            this.lbttmsthue = lbttmsthue;
            this.lbbhphap = lbbhphap;
        }

        public String nbmst() {
            return nbmst;
        }

        public String tthang() {
            return tthang;
        }

        public String dthang() {
            return dthang;
        }

        public Boolean lbttmsthue() {
            return lbttmsthue;
        }

        public Boolean lbbhphap() {
            return lbbhphap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SalesListRequest)) {
                return false;
            }
            SalesListRequest other = (SalesListRequest) o;
            return Objects.equals(nbmst, other.nbmst)
                    && Objects.equals(tthang, other.tthang)
                    && Objects.equals(dthang, other.dthang)
                    && Objects.equals(lbttmsthue, other.lbttmsthue)
                    && Objects.equals(lbbhphap, other.lbbhphap);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(nbmst);
            result = 31 * result + Objects.hashCode(tthang);
            result = 31 * result + Objects.hashCode(dthang);
            result = 31 * result + Objects.hashCode(lbttmsthue);
            result = 31 * result + Objects.hashCode(lbbhphap);
            return result;
        }

        @Override
        public String toString() {
            return "SalesListRequest[nbmst=" + nbmst
                    + ", tthang=" + tthang
                    + ", dthang=" + dthang
                    + ", lbttmsthue=" + lbttmsthue
                    + ", lbbhphap=" + lbbhphap + "]";
        }
    }

    /** HD7 body (HD7!C14–C16). */
    public static final class DeclarationSuggestionRequest {
        @NotBlank
        @Pattern(regexp = MST_REGEX, message = "mst không đúng định dạng MST")
        @JsonProperty("mst")
        private final String mst;
        @NotBlank
        @Pattern(regexp = MONTH_REGEX, message = "tthang phải có dạng MM/YYYY")
        @JsonProperty("tthang")
        private final String tthang;
        @NotBlank
        @Pattern(regexp = MONTH_REGEX, message = "dthang phải có dạng MM/YYYY")
        @JsonProperty("dthang")
        private final String dthang;

        @JsonCreator
        public DeclarationSuggestionRequest(@JsonProperty("mst") String mst, @JsonProperty("tthang") String tthang,
                @JsonProperty("dthang") String dthang) {
            this.mst = mst;
            this.tthang = tthang;
            this.dthang = dthang;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DeclarationSuggestionRequest)) {
                return false;
            }
            DeclarationSuggestionRequest other = (DeclarationSuggestionRequest) o;
            return Objects.equals(mst, other.mst)
                    && Objects.equals(tthang, other.tthang)
                    && Objects.equals(dthang, other.dthang);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(mst);
            result = 31 * result + Objects.hashCode(tthang);
            result = 31 * result + Objects.hashCode(dthang);
            return result;
        }

        @Override
        public String toString() {
            return "DeclarationSuggestionRequest[mst=" + mst
                    + ", tthang=" + tthang
                    + ", dthang=" + dthang + "]";
        }
    }

    /** HD8 body (HD8!C14–C17). */
    public static final class PurchaseVerificationRequest {
        @NotBlank
        @Pattern(regexp = MST_REGEX, message = "nmmst không đúng định dạng MST")
        @JsonProperty("nmmst")
        private final String nmmst;
        @NotBlank
        @Pattern(regexp = DATE_REGEX, message = "tngay phải có dạng yyyy-MM-dd")
        @JsonProperty("tngay")
        private final String tngay;
        @NotBlank
        @Pattern(regexp = DATE_REGEX, message = "dngay phải có dạng yyyy-MM-dd")
        @JsonProperty("dngay")
        private final String dngay;
        @NotBlank
        @Size(max = 1024)
        @JsonProperty("s3url")
        private final String s3url;

        @JsonCreator
        public PurchaseVerificationRequest(@JsonProperty("nmmst") String nmmst, @JsonProperty("tngay") String tngay,
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
            if (!(o instanceof PurchaseVerificationRequest)) {
                return false;
            }
            PurchaseVerificationRequest other = (PurchaseVerificationRequest) o;
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
            return "PurchaseVerificationRequest[nmmst=" + nmmst
                    + ", tngay=" + tngay
                    + ", dngay=" + dngay
                    + ", s3url=" + s3url + "]";
        }
    }

    /** TMS body (TMS!C14). */
    public static final class RefundUpdateRequest {
        @NotBlank
        @Size(max = 1024)
        @JsonProperty("s3url")
        private final String s3url;

        @JsonCreator
        public RefundUpdateRequest(@JsonProperty("s3url") String s3url) {
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
            if (!(o instanceof RefundUpdateRequest)) {
                return false;
            }
            RefundUpdateRequest other = (RefundUpdateRequest) o;
            return Objects.equals(s3url, other.s3url);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(s3url);
            return result;
        }

        @Override
        public String toString() {
            return "RefundUpdateRequest[s3url=" + s3url + "]";
        }
    }

    /** data của HD1/HD5/GET job (HD1!C30–C36, JOB!C26–C32). */
    @JsonPropertyOrder({"jobId", "jobCode", "tthai", "mta", "tdkxuat", "url", "tsdong"})
    public static final class JobStatusData {
        @Schema(format = "uuid")
        @JsonProperty("jobId")
        private final String jobId;
        @JsonProperty("jobCode")
        private final String jobCode;
        @Schema(allowableValues = {"TIEP_NHAN", "DANG_XU_LY", "HOAN_THANH", "LOI"})
        @JsonProperty("tthai")
        private final String tthai;
        @JsonProperty("mta")
        private final String mta;
        @Schema(example = "2026-10-28 09:52:19")
        @JsonProperty("tdkxuat")
        private final String tdkxuat;
        @JsonProperty("url")
        private final String url;
        @JsonProperty("tsdong")
        private final Long tsdong;

        @JsonCreator
        public JobStatusData(@JsonProperty("jobId") String jobId, @JsonProperty("jobCode") String jobCode,
                @JsonProperty("tthai") String tthai, @JsonProperty("mta") String mta,
                @JsonProperty("tdkxuat") String tdkxuat, @JsonProperty("url") String url,
                @JsonProperty("tsdong") Long tsdong) {
            this.jobId = jobId;
            this.jobCode = jobCode;
            this.tthai = tthai;
            this.mta = mta;
            this.tdkxuat = tdkxuat;
            this.url = url;
            this.tsdong = tsdong;
        }

        public String jobId() {
            return jobId;
        }

        public String jobCode() {
            return jobCode;
        }

        public String tthai() {
            return tthai;
        }

        public String mta() {
            return mta;
        }

        public String tdkxuat() {
            return tdkxuat;
        }

        public String url() {
            return url;
        }

        public Long tsdong() {
            return tsdong;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof JobStatusData)) {
                return false;
            }
            JobStatusData other = (JobStatusData) o;
            return Objects.equals(jobId, other.jobId)
                    && Objects.equals(jobCode, other.jobCode)
                    && Objects.equals(tthai, other.tthai)
                    && Objects.equals(mta, other.mta)
                    && Objects.equals(tdkxuat, other.tdkxuat)
                    && Objects.equals(url, other.url)
                    && Objects.equals(tsdong, other.tsdong);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(jobId);
            result = 31 * result + Objects.hashCode(jobCode);
            result = 31 * result + Objects.hashCode(tthai);
            result = 31 * result + Objects.hashCode(mta);
            result = 31 * result + Objects.hashCode(tdkxuat);
            result = 31 * result + Objects.hashCode(url);
            result = 31 * result + Objects.hashCode(tsdong);
            return result;
        }

        @Override
        public String toString() {
            return "JobStatusData[jobId=" + jobId
                    + ", jobCode=" + jobCode
                    + ", tthai=" + tthai
                    + ", mta=" + mta
                    + ", tdkxuat=" + tdkxuat
                    + ", url=" + url
                    + ", tsdong=" + tsdong + "]";
        }
    }

    /** data của HD8/TMS (HD8!C29–C31, TMS!C26–C28). */
    @JsonPropertyOrder({"jobId", "jobCode", "tthai"})
    public static final class JobAckData {
        @Schema(format = "uuid")
        @JsonProperty("jobId")
        private final String jobId;
        @JsonProperty("jobCode")
        private final String jobCode;
        @Schema(allowableValues = {"TIEP_NHAN", "DANG_XU_LY", "HOAN_THANH", "LOI"})
        @JsonProperty("tthai")
        private final String tthai;

        @JsonCreator
        public JobAckData(@JsonProperty("jobId") String jobId, @JsonProperty("jobCode") String jobCode,
                @JsonProperty("tthai") String tthai) {
            this.jobId = jobId;
            this.jobCode = jobCode;
            this.tthai = tthai;
        }

        public String jobId() {
            return jobId;
        }

        public String jobCode() {
            return jobCode;
        }

        public String tthai() {
            return tthai;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof JobAckData)) {
                return false;
            }
            JobAckData other = (JobAckData) o;
            return Objects.equals(jobId, other.jobId)
                    && Objects.equals(jobCode, other.jobCode)
                    && Objects.equals(tthai, other.tthai);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(jobId);
            result = 31 * result + Objects.hashCode(jobCode);
            result = 31 * result + Objects.hashCode(tthai);
            return result;
        }

        @Override
        public String toString() {
            return "JobAckData[jobId=" + jobId
                    + ", jobCode=" + jobCode
                    + ", tthai=" + tthai + "]";
        }
    }

    /** Phần tử thttltsuat (HD3!C38–C40, cấu trúc theo HD3!E55). */
    @JsonPropertyOrder({"tsuat", "thtien", "tthue"})
    public static final class TaxRateDto {
        @JsonProperty("tsuat")
        private final String tsuat;
        @JsonProperty("thtien")
        private final BigDecimal thtien;
        @JsonProperty("tthue")
        private final BigDecimal tthue;

        @JsonCreator
        public TaxRateDto(@JsonProperty("tsuat") String tsuat, @JsonProperty("thtien") BigDecimal thtien,
                @JsonProperty("tthue") BigDecimal tthue) {
            this.tsuat = tsuat;
            this.thtien = thtien;
            this.tthue = tthue;
        }

        public String tsuat() {
            return tsuat;
        }

        public BigDecimal thtien() {
            return thtien;
        }

        public BigDecimal tthue() {
            return tthue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TaxRateDto)) {
                return false;
            }
            TaxRateDto other = (TaxRateDto) o;
            return Objects.equals(tsuat, other.tsuat)
                    && Objects.equals(thtien, other.thtien)
                    && Objects.equals(tthue, other.tthue);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(tsuat);
            result = 31 * result + Objects.hashCode(thtien);
            result = 31 * result + Objects.hashCode(tthue);
            return result;
        }

        @Override
        public String toString() {
            return "TaxRateDto[tsuat=" + tsuat
                    + ", thtien=" + thtien
                    + ", tthue=" + tthue + "]";
        }
    }

    /** Phần tử dshhdvu (HD3!C46–C49). */
    @JsonPropertyOrder({"ten", "thtcthue", "ltsuat", "tthue"})
    public static final class GoodsDto {
        @JsonProperty("ten")
        private final String ten;
        @JsonProperty("thtcthue")
        private final BigDecimal thtcthue;
        @JsonProperty("ltsuat")
        private final String ltsuat;
        @JsonProperty("tthue")
        private final BigDecimal tthue;

        @JsonCreator
        public GoodsDto(@JsonProperty("ten") String ten, @JsonProperty("thtcthue") BigDecimal thtcthue,
                @JsonProperty("ltsuat") String ltsuat, @JsonProperty("tthue") BigDecimal tthue) {
            this.ten = ten;
            this.thtcthue = thtcthue;
            this.ltsuat = ltsuat;
            this.tthue = tthue;
        }

        public String ten() {
            return ten;
        }

        public BigDecimal thtcthue() {
            return thtcthue;
        }

        public String ltsuat() {
            return ltsuat;
        }

        public BigDecimal tthue() {
            return tthue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof GoodsDto)) {
                return false;
            }
            GoodsDto other = (GoodsDto) o;
            return Objects.equals(ten, other.ten)
                    && Objects.equals(thtcthue, other.thtcthue)
                    && Objects.equals(ltsuat, other.ltsuat)
                    && Objects.equals(tthue, other.tthue);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(ten);
            result = 31 * result + Objects.hashCode(thtcthue);
            result = 31 * result + Objects.hashCode(ltsuat);
            result = 31 * result + Objects.hashCode(tthue);
            return result;
        }

        @Override
        public String toString() {
            return "GoodsDto[ten=" + ten
                    + ", thtcthue=" + thtcthue
                    + ", ltsuat=" + ltsuat
                    + ", tthue=" + tthue + "]";
        }
    }

    /** data của HD3 (HD3!C28–C51; API v3: {@code lddctthe} cuối — HD3!C51). */
    @JsonPropertyOrder({"nbmst", "khmshdon", "khhdon", "shdon", "tdlap", "nky", "tthai", "nbten", "tgtcthue",
            "tgtthue", "thttltsuat", "ttmstnban", "bhphap", "bhpldo", "bhpngay", "dshhdvu", "lddctthe"})
    public static final class InvoiceDetailData {
        @JsonProperty("nbmst")
        private final String nbmst;
        @JsonProperty("khmshdon")
        private final Integer khmshdon;
        @JsonProperty("khhdon")
        private final String khhdon;
        @JsonProperty("shdon")
        private final Long shdon;
        @Schema(example = "2026-10-28")
        @JsonProperty("tdlap")
        private final String tdlap;
        @Schema(example = "2026-10-28 09:52:19")
        @JsonProperty("nky")
        private final String nky;
        @JsonProperty("tthai")
        private final Integer tthai;
        @JsonProperty("nbten")
        private final String nbten;
        @JsonProperty("tgtcthue")
        private final BigDecimal tgtcthue;
        @JsonProperty("tgtthue")
        private final BigDecimal tgtthue;
        @JsonProperty("thttltsuat")
        private final List<TaxRateDto> thttltsuat;
        @JsonProperty("ttmstnban")
        private final String ttmstnban;
        @JsonProperty("bhphap")
        private final Integer bhphap;
        @JsonProperty("bhpldo")
        private final String bhpldo;
        @JsonProperty("bhpngay")
        private final String bhpngay;
        @JsonProperty("dshhdvu")
        private final List<GoodsDto> dshhdvu;
        @Schema(description = "Lí do điều chỉnh/thay thế hóa đơn (phục vụ hoàn thuế)")
        @JsonProperty("lddctthe")
        private final String lddctthe;

        @JsonCreator
        public InvoiceDetailData(@JsonProperty("nbmst") String nbmst, @JsonProperty("khmshdon") Integer khmshdon,
                @JsonProperty("khhdon") String khhdon, @JsonProperty("shdon") Long shdon,
                @JsonProperty("tdlap") String tdlap, @JsonProperty("nky") String nky,
                @JsonProperty("tthai") Integer tthai, @JsonProperty("nbten") String nbten,
                @JsonProperty("tgtcthue") BigDecimal tgtcthue, @JsonProperty("tgtthue") BigDecimal tgtthue,
                @JsonProperty("thttltsuat") List<TaxRateDto> thttltsuat, @JsonProperty("ttmstnban") String ttmstnban,
                @JsonProperty("bhphap") Integer bhphap, @JsonProperty("bhpldo") String bhpldo,
                @JsonProperty("bhpngay") String bhpngay, @JsonProperty("dshhdvu") List<GoodsDto> dshhdvu,
                @JsonProperty("lddctthe") String lddctthe) {
            this.nbmst = nbmst;
            this.khmshdon = khmshdon;
            this.khhdon = khhdon;
            this.shdon = shdon;
            this.tdlap = tdlap;
            this.nky = nky;
            this.tthai = tthai;
            this.nbten = nbten;
            this.tgtcthue = tgtcthue;
            this.tgtthue = tgtthue;
            this.thttltsuat = thttltsuat;
            this.ttmstnban = ttmstnban;
            this.bhphap = bhphap;
            this.bhpldo = bhpldo;
            this.bhpngay = bhpngay;
            this.dshhdvu = dshhdvu;
            this.lddctthe = lddctthe;
        }

        public String nbmst() {
            return nbmst;
        }

        public Integer khmshdon() {
            return khmshdon;
        }

        public String khhdon() {
            return khhdon;
        }

        public Long shdon() {
            return shdon;
        }

        public String tdlap() {
            return tdlap;
        }

        public String nky() {
            return nky;
        }

        public Integer tthai() {
            return tthai;
        }

        public String nbten() {
            return nbten;
        }

        public BigDecimal tgtcthue() {
            return tgtcthue;
        }

        public BigDecimal tgtthue() {
            return tgtthue;
        }

        public List<TaxRateDto> thttltsuat() {
            return thttltsuat;
        }

        public String ttmstnban() {
            return ttmstnban;
        }

        public Integer bhphap() {
            return bhphap;
        }

        public String bhpldo() {
            return bhpldo;
        }

        public String bhpngay() {
            return bhpngay;
        }

        public List<GoodsDto> dshhdvu() {
            return dshhdvu;
        }

        public String lddctthe() {
            return lddctthe;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InvoiceDetailData)) {
                return false;
            }
            InvoiceDetailData other = (InvoiceDetailData) o;
            return Objects.equals(nbmst, other.nbmst)
                    && Objects.equals(khmshdon, other.khmshdon)
                    && Objects.equals(khhdon, other.khhdon)
                    && Objects.equals(shdon, other.shdon)
                    && Objects.equals(tdlap, other.tdlap)
                    && Objects.equals(nky, other.nky)
                    && Objects.equals(tthai, other.tthai)
                    && Objects.equals(nbten, other.nbten)
                    && Objects.equals(tgtcthue, other.tgtcthue)
                    && Objects.equals(tgtthue, other.tgtthue)
                    && Objects.equals(thttltsuat, other.thttltsuat)
                    && Objects.equals(ttmstnban, other.ttmstnban)
                    && Objects.equals(bhphap, other.bhphap)
                    && Objects.equals(bhpldo, other.bhpldo)
                    && Objects.equals(bhpngay, other.bhpngay)
                    && Objects.equals(dshhdvu, other.dshhdvu)
                    && Objects.equals(lddctthe, other.lddctthe);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(nbmst);
            result = 31 * result + Objects.hashCode(khmshdon);
            result = 31 * result + Objects.hashCode(khhdon);
            result = 31 * result + Objects.hashCode(shdon);
            result = 31 * result + Objects.hashCode(tdlap);
            result = 31 * result + Objects.hashCode(nky);
            result = 31 * result + Objects.hashCode(tthai);
            result = 31 * result + Objects.hashCode(nbten);
            result = 31 * result + Objects.hashCode(tgtcthue);
            result = 31 * result + Objects.hashCode(tgtthue);
            result = 31 * result + Objects.hashCode(thttltsuat);
            result = 31 * result + Objects.hashCode(ttmstnban);
            result = 31 * result + Objects.hashCode(bhphap);
            result = 31 * result + Objects.hashCode(bhpldo);
            result = 31 * result + Objects.hashCode(bhpngay);
            result = 31 * result + Objects.hashCode(dshhdvu);
            result = 31 * result + Objects.hashCode(lddctthe);
            return result;
        }

        @Override
        public String toString() {
            return "InvoiceDetailData[nbmst=" + nbmst
                    + ", khmshdon=" + khmshdon
                    + ", khhdon=" + khhdon
                    + ", shdon=" + shdon
                    + ", tdlap=" + tdlap
                    + ", nky=" + nky
                    + ", tthai=" + tthai
                    + ", nbten=" + nbten
                    + ", tgtcthue=" + tgtcthue
                    + ", tgtthue=" + tgtthue
                    + ", thttltsuat=" + thttltsuat
                    + ", ttmstnban=" + ttmstnban
                    + ", bhphap=" + bhphap
                    + ", bhpldo=" + bhpldo
                    + ", bhpngay=" + bhpngay
                    + ", dshhdvu=" + dshhdvu
                    + ", lddctthe=" + lddctthe + "]";
        }
    }

    /** data.mvao của HD7 (HD7!C28–C31). */
    @JsonPropertyOrder({"tgtmvao", "tgttmvao", "slmvao"})
    public static final class PurchaseSummaryDto {
        @JsonProperty("tgtmvao")
        private final BigDecimal tgtmvao;
        @JsonProperty("tgttmvao")
        private final BigDecimal tgttmvao;
        @JsonProperty("slmvao")
        private final Long slmvao;

        @JsonCreator
        public PurchaseSummaryDto(@JsonProperty("tgtmvao") BigDecimal tgtmvao,
                @JsonProperty("tgttmvao") BigDecimal tgttmvao, @JsonProperty("slmvao") Long slmvao) {
            this.tgtmvao = tgtmvao;
            this.tgttmvao = tgttmvao;
            this.slmvao = slmvao;
        }

        public BigDecimal tgtmvao() {
            return tgtmvao;
        }

        public BigDecimal tgttmvao() {
            return tgttmvao;
        }

        public Long slmvao() {
            return slmvao;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PurchaseSummaryDto)) {
                return false;
            }
            PurchaseSummaryDto other = (PurchaseSummaryDto) o;
            return Objects.equals(tgtmvao, other.tgtmvao)
                    && Objects.equals(tgttmvao, other.tgttmvao)
                    && Objects.equals(slmvao, other.slmvao);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(tgtmvao);
            result = 31 * result + Objects.hashCode(tgttmvao);
            result = 31 * result + Objects.hashCode(slmvao);
            return result;
        }

        @Override
        public String toString() {
            return "PurchaseSummaryDto[tgtmvao=" + tgtmvao
                    + ", tgttmvao=" + tgttmvao
                    + ", slmvao=" + slmvao + "]";
        }
    }

    /** data.bra của HD7 (HD7!C32–C41). */
    @JsonPropertyOrder({"tgtbr0", "tgtbr5", "tgttbr5", "tgtbr10", "tgttbr10", "tgtbrkct", "tgtbrkkknt", "tgtbrktgtt",
            "tgtbrktpvdc"})
    public static final class SalesSummaryDto {
        @JsonProperty("tgtbr0")
        private final BigDecimal tgtbr0;
        @JsonProperty("tgtbr5")
        private final BigDecimal tgtbr5;
        @JsonProperty("tgttbr5")
        private final BigDecimal tgttbr5;
        @JsonProperty("tgtbr10")
        private final BigDecimal tgtbr10;
        @JsonProperty("tgttbr10")
        private final BigDecimal tgttbr10;
        @JsonProperty("tgtbrkct")
        private final BigDecimal tgtbrkct;
        @JsonProperty("tgtbrkkknt")
        private final BigDecimal tgtbrkkknt;
        @JsonProperty("tgtbrktgtt")
        private final BigDecimal tgtbrktgtt;
        @JsonProperty("tgtbrktpvdc")
        private final BigDecimal tgtbrktpvdc;

        @JsonCreator
        public SalesSummaryDto(@JsonProperty("tgtbr0") BigDecimal tgtbr0, @JsonProperty("tgtbr5") BigDecimal tgtbr5,
                @JsonProperty("tgttbr5") BigDecimal tgttbr5, @JsonProperty("tgtbr10") BigDecimal tgtbr10,
                @JsonProperty("tgttbr10") BigDecimal tgttbr10, @JsonProperty("tgtbrkct") BigDecimal tgtbrkct,
                @JsonProperty("tgtbrkkknt") BigDecimal tgtbrkkknt, @JsonProperty("tgtbrktgtt") BigDecimal tgtbrktgtt,
                @JsonProperty("tgtbrktpvdc") BigDecimal tgtbrktpvdc) {
            this.tgtbr0 = tgtbr0;
            this.tgtbr5 = tgtbr5;
            this.tgttbr5 = tgttbr5;
            this.tgtbr10 = tgtbr10;
            this.tgttbr10 = tgttbr10;
            this.tgtbrkct = tgtbrkct;
            this.tgtbrkkknt = tgtbrkkknt;
            this.tgtbrktgtt = tgtbrktgtt;
            this.tgtbrktpvdc = tgtbrktpvdc;
        }

        public BigDecimal tgtbr0() {
            return tgtbr0;
        }

        public BigDecimal tgtbr5() {
            return tgtbr5;
        }

        public BigDecimal tgttbr5() {
            return tgttbr5;
        }

        public BigDecimal tgtbr10() {
            return tgtbr10;
        }

        public BigDecimal tgttbr10() {
            return tgttbr10;
        }

        public BigDecimal tgtbrkct() {
            return tgtbrkct;
        }

        public BigDecimal tgtbrkkknt() {
            return tgtbrkkknt;
        }

        public BigDecimal tgtbrktgtt() {
            return tgtbrktgtt;
        }

        public BigDecimal tgtbrktpvdc() {
            return tgtbrktpvdc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SalesSummaryDto)) {
                return false;
            }
            SalesSummaryDto other = (SalesSummaryDto) o;
            return Objects.equals(tgtbr0, other.tgtbr0)
                    && Objects.equals(tgtbr5, other.tgtbr5)
                    && Objects.equals(tgttbr5, other.tgttbr5)
                    && Objects.equals(tgtbr10, other.tgtbr10)
                    && Objects.equals(tgttbr10, other.tgttbr10)
                    && Objects.equals(tgtbrkct, other.tgtbrkct)
                    && Objects.equals(tgtbrkkknt, other.tgtbrkkknt)
                    && Objects.equals(tgtbrktgtt, other.tgtbrktgtt)
                    && Objects.equals(tgtbrktpvdc, other.tgtbrktpvdc);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(tgtbr0);
            result = 31 * result + Objects.hashCode(tgtbr5);
            result = 31 * result + Objects.hashCode(tgttbr5);
            result = 31 * result + Objects.hashCode(tgtbr10);
            result = 31 * result + Objects.hashCode(tgttbr10);
            result = 31 * result + Objects.hashCode(tgtbrkct);
            result = 31 * result + Objects.hashCode(tgtbrkkknt);
            result = 31 * result + Objects.hashCode(tgtbrktgtt);
            result = 31 * result + Objects.hashCode(tgtbrktpvdc);
            return result;
        }

        @Override
        public String toString() {
            return "SalesSummaryDto[tgtbr0=" + tgtbr0
                    + ", tgtbr5=" + tgtbr5
                    + ", tgttbr5=" + tgttbr5
                    + ", tgtbr10=" + tgtbr10
                    + ", tgttbr10=" + tgttbr10
                    + ", tgtbrkct=" + tgtbrkct
                    + ", tgtbrkkknt=" + tgtbrkkknt
                    + ", tgtbrktgtt=" + tgtbrktgtt
                    + ", tgtbrktpvdc=" + tgtbrktpvdc + "]";
        }
    }

    /**
     * data của HD7. `lddctthe` (sheet 06/11 HD7!C42, String, N — lý do điều chỉnh/thay thế, SI-64): sheet ghi Level 1,
     * không có Parent; đặt trong data cạnh mvao/bra (envelope dùng chung mọi API). Chưa có nguồn Doris và chưa có quy
     * tắc gộp cho số liệu tổng hợp ⇒ luôn null (có mặt trong JSON).
     */
    @JsonPropertyOrder({"mvao", "bra", "lddctthe"})
    public static final class DeclarationSuggestionData {
        @JsonProperty("mvao")
        private final PurchaseSummaryDto mvao;
        @JsonProperty("bra")
        private final SalesSummaryDto bra;
        @JsonProperty("lddctthe")
        private final String lddctthe;

        public DeclarationSuggestionData(PurchaseSummaryDto mvao, SalesSummaryDto bra) {
            this(mvao, bra, null);
        }

        @JsonCreator
        public DeclarationSuggestionData(@JsonProperty("mvao") PurchaseSummaryDto mvao,
                @JsonProperty("bra") SalesSummaryDto bra, @JsonProperty("lddctthe") String lddctthe) {
            this.mvao = mvao;
            this.bra = bra;
            this.lddctthe = lddctthe;
        }

        public PurchaseSummaryDto mvao() {
            return mvao;
        }

        public SalesSummaryDto bra() {
            return bra;
        }

        public String lddctthe() {
            return lddctthe;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DeclarationSuggestionData)) {
                return false;
            }
            DeclarationSuggestionData other = (DeclarationSuggestionData) o;
            return Objects.equals(mvao, other.mvao)
                    && Objects.equals(bra, other.bra)
                    && Objects.equals(lddctthe, other.lddctthe);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mvao, bra, lddctthe);
        }

        @Override
        public String toString() {
            return "DeclarationSuggestionData[mvao=" + mvao
                    + ", bra=" + bra + ", lddctthe=" + lddctthe + "]";
        }
    }

    public static List<String> jobStatusFields() {
        return Jdk8Compat.listOf("jobId", "jobCode", "tthai", "mta", "tdkxuat", "url", "tsdong");
    }
}
