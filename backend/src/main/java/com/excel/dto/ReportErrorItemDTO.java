package com.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 上报异常明细项
 * 每条异常包含：行号、医保编号（数据编号）、错误码、错误描述、处理建议，
 * 并携带完整业务字段，便于前端直接回显修正
 */
@Data
@Builder
public class ReportErrorItemDTO {

    /**
     * 数据ID（用于修正数据）
     */
    private Long id;

    /**
     * Excel原始行号
     */
    private Integer rowIndex;

    /**
     * 医保编号（数据编号）
     */
    private String dataCode;

    /**
     * 姓名
     */
    private String name;

    /**
     * 身份证号
     */
    private String idCard;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 地址
     */
    private String address;

    /**
     * 备注
     */
    private String remark;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误描述
     */
    private String errorMsg;

    /**
     * 处理建议
     */
    private String suggestion;

    /**
     * 上报时间
     */
    private LocalDateTime reportTime;
}
