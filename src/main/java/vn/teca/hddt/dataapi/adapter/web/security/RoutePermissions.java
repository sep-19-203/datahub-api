package vn.teca.hddt.dataapi.adapter.web.security;

import java.util.Optional;

import vn.teca.hddt.dataapi.application.security.ApiPermission;

/** Quyền yêu cầu theo (method, path) của 7 API. */
public final class RoutePermissions {
    public static final String BASE = "/hthue/v1";

    private RoutePermissions() {
    }

    public static Optional<ApiPermission> required(String method, String path) {
        if ("POST".equals(method)) {
            switch (path) {
                case BASE + "/mua-vao":
                    return Optional.of(ApiPermission.PURCHASE_LIST);
                case BASE + "/ban-ra":
                    return Optional.of(ApiPermission.SALES_LIST);
                case BASE + "/goi-y":
                    return Optional.of(ApiPermission.DECLARATION_SUGGEST);
                case BASE + "/mua-vao/kiem-tra":
                    return Optional.of(ApiPermission.PURCHASE_VERIFY);
                case BASE + "/cap-nhat-hoa-don-hoan-thue":
                    return Optional.of(ApiPermission.REFUND_UPDATE);
                default:
                    return Optional.empty();
            }
        }
        if ("GET".equals(method)) {
            if (path.equals(BASE + "/chi-tiet")) {
                return Optional.of(ApiPermission.INVOICE_DETAIL);
            }
            if (path.startsWith(BASE + "/job/") && path.indexOf('/', (BASE + "/job/").length()) < 0) {
                return Optional.of(ApiPermission.JOB_READ);
            }
        }
        return Optional.empty();
    }
}
