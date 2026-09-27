-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- Excel数据表
CREATE TABLE IF NOT EXISTS `excel_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `data_code` VARCHAR(50) NOT NULL COMMENT '数据编号',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `id_card` VARCHAR(20) COMMENT '身份证号',
    `medical_insurance_no` VARCHAR(50) COMMENT '医保编号',
    `phone` VARCHAR(20) COMMENT '手机号',
    `amount` DECIMAL(15,2) COMMENT '金额',
    `address` VARCHAR(200) COMMENT '地址',
    `remark` VARCHAR(500) COMMENT '备注',
    `row_no` INT COMMENT '原始Excel行号（含表头）',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '导入批次号',
    `report_status` TINYINT DEFAULT 0 COMMENT '上报状态：0-待上报 1-已上报 2-上报失败',
    `report_message` VARCHAR(500) COMMENT '上报结果信息',
    `report_error_code` VARCHAR(30) COMMENT '最近一次国家平台返回的错误码',
    `report_time` DATETIME COMMENT '上报时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    INDEX `idx_batch_no` (`batch_no`),
    INDEX `idx_batch_status` (`batch_no`, `report_status`),
    INDEX `idx_report_status` (`report_status`),
    INDEX `idx_data_code` (`data_code`),
    INDEX `idx_medical_insurance_no` (`medical_insurance_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Excel数据表';

-- 国家平台上报异常明细表（结构化异常回写，每行异常一条记录）
CREATE TABLE IF NOT EXISTS `report_error` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '导入批次号',
    `data_id` BIGINT NOT NULL COMMENT 'excel_data主键ID',
    `row_no` INT COMMENT '原始Excel行号（含表头）',
    `medical_insurance_no` VARCHAR(50) COMMENT '医保编号',
    `data_code` VARCHAR(50) COMMENT '数据编号',
    `name` VARCHAR(50) COMMENT '姓名',
    `error_code` VARCHAR(30) NOT NULL COMMENT '国家平台错误码',
    `error_desc` VARCHAR(500) COMMENT '错误描述',
    `suggestion` VARCHAR(500) COMMENT '处理建议',
    `resolved` TINYINT DEFAULT 0 COMMENT '是否已处理：0-未处理（仍异常） 1-已处理（重送成功或忽略）',
    `report_time` DATETIME COMMENT '国家平台返回异常的时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    INDEX `idx_re_batch_resolved` (`batch_no`, `resolved`),
    INDEX `idx_re_batch_code` (`batch_no`, `error_code`),
    INDEX `idx_re_data_id` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国家平台上报异常明细表';

-- 兼容已存在的旧库：幂等补列（重复执行不报错）
DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @sql = ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;
CALL add_column_if_missing('excel_data', 'medical_insurance_no', 'ALTER TABLE `excel_data` ADD COLUMN `medical_insurance_no` VARCHAR(50) COMMENT ''医保编号'' AFTER `id_card`');
CALL add_column_if_missing('excel_data', 'row_no', 'ALTER TABLE `excel_data` ADD COLUMN `row_no` INT COMMENT ''原始Excel行号（含表头）'' AFTER `remark`');
CALL add_column_if_missing('excel_data', 'report_error_code', 'ALTER TABLE `excel_data` ADD COLUMN `report_error_code` VARCHAR(30) COMMENT ''最近一次国家平台返回的错误码'' AFTER `report_message`');
CALL add_column_if_missing('import_record', 'report_total_count', 'ALTER TABLE `import_record` ADD COLUMN `report_total_count` INT DEFAULT 0 COMMENT ''上报总数'' AFTER `fail_count`');
CALL add_column_if_missing('import_record', 'report_success_count', 'ALTER TABLE `import_record` ADD COLUMN `report_success_count` INT DEFAULT 0 COMMENT ''上报成功数'' AFTER `report_total_count`');
CALL add_column_if_missing('import_record', 'report_fail_count', 'ALTER TABLE `import_record` ADD COLUMN `report_fail_count` INT DEFAULT 0 COMMENT ''上报异常数'' AFTER `report_success_count`');
DROP PROCEDURE IF EXISTS add_column_if_missing;

-- 导入记录表
CREATE TABLE IF NOT EXISTS `import_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号',
    `file_name` VARCHAR(200) COMMENT '文件名',
    `file_size` BIGINT COMMENT '文件大小（字节）',
    `total_count` INT DEFAULT 0 COMMENT '总记录数',
    `success_count` INT DEFAULT 0 COMMENT '导入成功数量',
    `fail_count` INT DEFAULT 0 COMMENT '导入失败数量',
    `report_total_count` INT DEFAULT 0 COMMENT '上报总数',
    `report_success_count` INT DEFAULT 0 COMMENT '上报成功数',
    `report_fail_count` INT DEFAULT 0 COMMENT '上报异常数（未处理）',
    `status` TINYINT DEFAULT 0 COMMENT '导入状态：0-处理中 1-完成 2-失败',
    `error_details` TEXT COMMENT '错误详情',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(50) COMMENT '操作人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入记录表';

-- 管理员用户由应用启动时通过 DataInitializer 自动创建
-- 账号: admin  密码: admin123
