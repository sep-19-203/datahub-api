package vn.teca.hddt.dataapi.application.security;

/** Quyền gọi từng API (phân quyền TTHC / TMS — SI-17, SI-23). */
public enum ApiPermission {
    PURCHASE_LIST, INVOICE_DETAIL, SALES_LIST, DECLARATION_SUGGEST, PURCHASE_VERIFY, REFUND_UPDATE, JOB_READ
}
