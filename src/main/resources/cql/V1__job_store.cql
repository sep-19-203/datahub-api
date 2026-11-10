-- Kho metadata job trên DSE (keyspace ${KS}) — thay PostgreSQL (TD-API-03b). Doris 4.0.8 bị loại sau thực nghiệm:
-- INSERT trùng khóa ghi đè, UPDATE có điều kiện chạy song song cả hai đều "thành công" (không CAS).
-- Chống trùng / nhận job / rào ghi dùng Lightweight Transaction (Paxos, LOCAL_SERIAL).
-- ${JOB_TTL}: thời gian giữ metadata job (giây) — đồng thời là cửa sổ idempotency X-Request-Id.
CREATE KEYSPACE IF NOT EXISTS ${KS} WITH replication = ${REPLICATION} AND durable_writes = true;

CREATE TABLE IF NOT EXISTS ${KS}.api_job (
    job_id uuid PRIMARY KEY,
    shard int,
    job_type text,
    job_code text,
    client_id text,
    request_id text,
    request_hash text,
    request_payload text,
    subject_mst text,
    state text,
    attempt_no int,
    max_attempts int,
    created_at timestamp,
    updated_at timestamp,
    started_at timestamp,
    finished_at timestamp,
    next_attempt_at timestamp,
    worker_id text,
    lease_until timestamp,
    progress text,
    result_bucket text,
    result_prefix text,
    result_url text,
    result_manifest text,
    row_count bigint,
    description text,
    error_code text,
    error_message text,
    expired_at timestamp
) WITH default_time_to_live = ${JOB_TTL};

-- Idempotency: (client, X-Request-Id) → job, ghi bằng INSERT ... IF NOT EXISTS.
CREATE TABLE IF NOT EXISTS ${KS}.api_job_by_request (
    client_id text,
    request_id text,
    job_id uuid,
    created_at timestamp,
    PRIMARY KEY ((client_id, request_id))
) WITH default_time_to_live = ${JOB_TTL};

-- Chỉ mục job chưa kết thúc (nhỏ) để worker tìm job sẵn sàng / hết lease; xóa khi job kết thúc.
CREATE TABLE IF NOT EXISTS ${KS}.api_job_active (
    shard int,
    job_id uuid,
    job_type text,
    client_id text,
    subject_mst text,
    created_at timestamp,
    PRIMARY KEY ((shard), job_id)
);

-- Nhật ký chuyển trạng thái (audit theo job).
CREATE TABLE IF NOT EXISTS ${KS}.api_job_event (
    job_id uuid,
    event_id timeuuid,
    event text,
    attempt_no int,
    worker_id text,
    detail text,
    PRIMARY KEY ((job_id), event_id)
) WITH CLUSTERING ORDER BY (event_id ASC) AND default_time_to_live = ${JOB_TTL};

-- Job thành công theo ngày kết thúc (janitor dọn file kết quả quá hạn).
CREATE TABLE IF NOT EXISTS ${KS}.api_job_finished (
    day date,
    finished_at timestamp,
    job_id uuid,
    PRIMARY KEY ((day), finished_at, job_id)
) WITH default_time_to_live = ${JOB_TTL};

-- Sổ xử lý TMS theo lô (SI-15): RECEIVED → VALIDATED/REJECTED → APPLY_BLOCKED → APPLIED. Không TTL (audit).
CREATE TABLE IF NOT EXISTS ${KS}.refund_batch (
    job_id uuid PRIMARY KEY,
    source_url text,
    items_attempt int,
    content_sha256 text,
    object_count int,
    total_bytes bigint,
    row_count bigint,
    valid_count bigint,
    invalid_count bigint,
    not_found_count bigint,
    ambiguous_count bigint,
    duplicate_count bigint,
    stage text,
    created_at timestamp,
    updated_at timestamp
);

-- Từng dòng TMS; khóa theo lần thử ⇒ lần thử lại không cần xóa hàng loạt; bucket = line_no / 10000 (giới hạn cỡ partition).
CREATE TABLE IF NOT EXISTS ${KS}.refund_item (
    job_id uuid,
    attempt_no int,
    bucket int,
    line_no bigint,
    nbmst text,
    khmshdon int,
    khhdon text,
    shdon bigint,
    tdlap date,
    item_status text,
    detail text,
    doris_loai_hd text,
    doris_id text,
    PRIMARY KEY ((job_id, attempt_no, bucket), line_no)
);

-- Một nội dung danh sách chỉ được ghi sổ hoàn một lần (INSERT ... IF NOT EXISTS).
CREATE TABLE IF NOT EXISTS ${KS}.refund_applied_content (
    content_sha256 text PRIMARY KEY,
    job_id uuid,
    applied_at timestamp
);
