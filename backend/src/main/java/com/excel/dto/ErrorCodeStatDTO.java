package com.excel.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 错误码统计项（用于前端筛选项，展示各错误码的异常数量）
 */
@Data
@Builder
public class ErrorCodeStatDTO {

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
     * 该错误码的异常数量
     */
    private Long count;
}
