package com.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 上报异常数据导出DTO
 * 包含行号、错误码、错误描述、处理建议，方便用户线下修正后对照处理
 */
@Data
public class ErrorExportDTO {

    @ExcelProperty(value = "行号", index = 0)
    @ColumnWidth(8)
    private Integer rowIndex;

    @ExcelProperty(value = "医保编号", index = 1)
    @ColumnWidth(15)
    private String dataCode;

    @ExcelProperty(value = "姓名", index = 2)
    @ColumnWidth(12)
    private String name;

    @ExcelProperty(value = "身份证号", index = 3)
    @ColumnWidth(22)
    private String idCard;

    @ExcelProperty(value = "手机号", index = 4)
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty(value = "金额", index = 5)
    @ColumnWidth(12)
    private BigDecimal amount;

    @ExcelProperty(value = "地址", index = 6)
    @ColumnWidth(30)
    private String address;

    @ExcelProperty(value = "备注", index = 7)
    @ColumnWidth(25)
    private String remark;

    @ExcelProperty(value = "错误码", index = 8)
    @ColumnWidth(10)
    private String errorCode;

    @ExcelProperty(value = "错误描述", index = 9)
    @ColumnWidth(30)
    private String errorMsg;

    @ExcelProperty(value = "处理建议", index = 10)
    @ColumnWidth(45)
    private String suggestion;
}
