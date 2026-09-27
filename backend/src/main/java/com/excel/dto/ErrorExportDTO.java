package com.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 国家平台上报异常导出（含行号、医保编号、错误码、错误描述、处理建议）
 */
@Data
public class ErrorExportDTO {

    @ExcelProperty(value = "行号", index = 0)
    @ColumnWidth(8)
    private Integer rowNo;

    @ExcelProperty(value = "医保编号", index = 1)
    @ColumnWidth(25)
    private String medicalInsuranceNo;

    @ExcelProperty(value = "数据编号", index = 2)
    @ColumnWidth(15)
    private String dataCode;

    @ExcelProperty(value = "姓名", index = 3)
    @ColumnWidth(12)
    private String name;

    @ExcelProperty(value = "身份证号", index = 4)
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty(value = "手机号", index = 5)
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty(value = "金额", index = 6)
    @ColumnWidth(12)
    private BigDecimal amount;

    @ExcelProperty(value = "地址", index = 7)
    @ColumnWidth(30)
    private String address;

    @ExcelProperty(value = "备注", index = 8)
    @ColumnWidth(25)
    private String remark;

    @ExcelProperty(value = "错误码", index = 9)
    @ColumnWidth(12)
    private String errorCode;

    @ExcelProperty(value = "错误描述", index = 10)
    @ColumnWidth(30)
    private String errorDesc;

    @ExcelProperty(value = "处理建议", index = 11)
    @ColumnWidth(50)
    private String suggestion;
}
