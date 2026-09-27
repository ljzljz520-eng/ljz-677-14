CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS excel_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    id_card VARCHAR(20),
    medical_insurance_no VARCHAR(50),
    phone VARCHAR(20),
    amount DECIMAL(15,2),
    address VARCHAR(200),
    remark VARCHAR(500),
    row_no INT,
    batch_no VARCHAR(50) NOT NULL,
    report_status TINYINT DEFAULT 0,
    report_message VARCHAR(500),
    report_error_code VARCHAR(30),
    report_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ed_batch ON excel_data (batch_no, report_status);

CREATE TABLE IF NOT EXISTS report_error (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL,
    data_id BIGINT NOT NULL,
    row_no INT,
    medical_insurance_no VARCHAR(50),
    data_code VARCHAR(50),
    name VARCHAR(50),
    error_code VARCHAR(30) NOT NULL,
    error_desc VARCHAR(500),
    suggestion VARCHAR(500),
    resolved TINYINT DEFAULT 0,
    report_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_re_batch_resolved ON report_error (batch_no, resolved);
CREATE INDEX IF NOT EXISTS idx_re_batch_code ON report_error (batch_no, error_code);

CREATE TABLE IF NOT EXISTS import_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL UNIQUE,
    file_name VARCHAR(200),
    file_size BIGINT,
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    report_total_count INT DEFAULT 0,
    report_success_count INT DEFAULT 0,
    report_fail_count INT DEFAULT 0,
    status TINYINT DEFAULT 0,
    error_details CLOB,
    operator_id BIGINT,
    operator_name VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
