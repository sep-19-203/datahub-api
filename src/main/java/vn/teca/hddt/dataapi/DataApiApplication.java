package vn.teca.hddt.dataapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.cassandra.CassandraHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;

/** Dịch vụ API dữ liệu HĐĐT (HD1, HD3, HD5, HD7, HD8, TMS, job) — Doris 4.0.8 → APIC → TTHC/TMS. */
// CqlSession của kho job tự dựng (DseConfig); không để Spring Boot tạo phiên mặc định tới 127.0.0.1:9042.
@SpringBootApplication(exclude = {CassandraAutoConfiguration.class, CassandraHealthContributorAutoConfiguration.class})
public class DataApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataApiApplication.class, args);
    }
}
