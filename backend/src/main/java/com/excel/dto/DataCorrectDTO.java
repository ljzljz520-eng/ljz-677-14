package com.excel.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 异常行在线修正入参
 */
@Data
public class DataCorrectDTO {

    /**
     * 姓名（必填）
     */
    private String name;

    /**
     * 身份证号
     */
    private String idCard;

    /**
     * 医保编号
     */
    private String medicalInsuranceNo;

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
}
