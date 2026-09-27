package com.excel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 国家平台上报异常明细
 * 每条异常包含：行号、医保编号、错误码、错误描述、处理建议
 */
@Data
@TableName("report_error")
public class ReportError {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 导入批次号
     */
    private String batchNo;

    /**
     * excel_data 主键ID
     */
    private Long dataId;

    /**
     * 原始Excel行号（含表头）
     */
    private Integer rowNo;

    /**
     * 医保编号
     */
    private String medicalInsuranceNo;

    /**
     * 数据编号
     */
    private String dataCode;

    /**
     * 姓名
     */
    private String name;

    /**
     * 国家平台错误码
     */
    private String errorCode;

    /**
     * 错误描述
     */
    private String errorDesc;

    /**
     * 处理建议
     */
    private String suggestion;

    /**
     * 是否已处理：0-未处理（仍异常） 1-已处理（重送成功或忽略）
     */
    private Integer resolved;

    /**
     * 国家平台返回异常的时间
     */
    private LocalDateTime reportTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
