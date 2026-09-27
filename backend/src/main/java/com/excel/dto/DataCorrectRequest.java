package com.excel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 异常数据修正请求
 * 用户修正异常行数据后，该行仍保持"上报失败"状态，重送时仅发送异常行
 */
@Data
public class DataCorrectRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过50个字符")
    private String name;

    @Size(max = 18, message = "身份证号不能超过18位")
    private String idCard;

    @Size(max = 11, message = "手机号不能超过11位")
    private String phone;

    @PositiveOrZero(message = "金额不能为负数")
    private BigDecimal amount;

    @Size(max = 200, message = "地址不能超过200个字符")
    private String address;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
