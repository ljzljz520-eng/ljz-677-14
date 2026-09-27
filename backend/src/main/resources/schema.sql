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
    `data_code` VARCHAR(50) NOT NULL COMMENT '数据编号（医保业务编号）',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `id_card` VARCHAR(20) COMMENT '身份证号',
    `phone` VARCHAR(20) COMMENT '手机号',
    `amount` DECIMAL(15,2) COMMENT '金额',
    `address` VARCHAR(200) COMMENT '地址',
    `remark` VARCHAR(500) COMMENT '备注',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '导入批次号',
    `row_index` INT COMMENT 'Excel原始行号（用于异常定位）',
    `report_status` TINYINT DEFAULT 0 COMMENT '上报状态：0-待上报 1-已上报 2-上报失败',
    `report_message` VARCHAR(500) COMMENT '上报结果信息（错误描述）',
    `error_code` VARCHAR(20) COMMENT '国家平台返回的错误码',
    `report_time` DATETIME COMMENT '上报时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    INDEX `idx_batch_no` (`batch_no`),
    INDEX `idx_report_status` (`report_status`),
    INDEX `idx_data_code` (`data_code`),
    INDEX `idx_error_code` (`error_code`),
    INDEX `idx_batch_status_code` (`batch_no`, `report_status`, `error_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Excel数据表';

-- 已有环境升级脚本（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，已存在列时请跳过对应语句）：
-- ALTER TABLE `excel_data` ADD COLUMN `row_index` INT COMMENT 'Excel原始行号（用于异常定位）' AFTER `batch_no`;
-- ALTER TABLE `excel_data` ADD COLUMN `error_code` VARCHAR(20) COMMENT '国家平台返回的错误码' AFTER `report_message`;
-- ALTER TABLE `excel_data` ADD INDEX `idx_error_code` (`error_code`);
-- ALTER TABLE `excel_data` ADD INDEX `idx_batch_status_code` (`batch_no`, `report_status`, `error_code`);

-- 导入记录表
CREATE TABLE IF NOT EXISTS `import_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号',
    `file_name` VARCHAR(200) COMMENT '文件名',
    `file_size` BIGINT COMMENT '文件大小（字节）',
    `total_count` INT DEFAULT 0 COMMENT '总记录数',
    `success_count` INT DEFAULT 0 COMMENT '成功数量',
    `fail_count` INT DEFAULT 0 COMMENT '失败数量',
    `status` TINYINT DEFAULT 0 COMMENT '导入状态：0-处理中 1-完成 2-失败',
    `error_details` TEXT COMMENT '导入阶段错误详情',
    `report_total_count` INT DEFAULT 0 COMMENT '上报总数',
    `report_success_count` INT DEFAULT 0 COMMENT '上报成功数',
    `report_fail_count` INT DEFAULT 0 COMMENT '上报失败数（待处理异常数）',
    `report_error_details` MEDIUMTEXT COMMENT '上报异常明细（JSON数组：行号/医保编号/错误码/错误描述/处理建议）',
    `last_report_time` DATETIME COMMENT '最近一次上报时间',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(50) COMMENT '操作人姓名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否 1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入记录表';

-- 已有环境升级脚本：
-- ALTER TABLE `import_record` ADD COLUMN `report_total_count` INT DEFAULT 0 COMMENT '上报总数' AFTER `error_details`;
-- ALTER TABLE `import_record` ADD COLUMN `report_success_count` INT DEFAULT 0 COMMENT '上报成功数' AFTER `report_total_count`;
-- ALTER TABLE `import_record` ADD COLUMN `report_fail_count` INT DEFAULT 0 COMMENT '上报失败数（待处理异常数）' AFTER `report_success_count`;
-- ALTER TABLE `import_record` ADD COLUMN `report_error_details` MEDIUMTEXT COMMENT '上报异常明细（JSON数组）' AFTER `report_fail_count`;
-- ALTER TABLE `import_record` ADD COLUMN `last_report_time` DATETIME COMMENT '最近一次上报时间' AFTER `report_error_details`;

-- 管理员用户由应用启动时通过 DataInitializer 自动创建
-- 账号: admin  密码: admin123
