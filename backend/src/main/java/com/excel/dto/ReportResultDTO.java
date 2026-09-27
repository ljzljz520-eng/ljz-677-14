package com.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReportResultDTO {

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 本次上报总数
     */
    private Integer totalCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failCount;

    /**
     * 上报状态
     */
    private String status;

    /**
     * 异常明细列表（结构化：行号/医保编号/错误码/错误描述/处理建议）
     */
    private List<ReportErrorItem> errorList;

    /**
     * 提示消息
     */
    private String message;

    @Data
    @Builder
    public static class ReportErrorItem {
        /**
         * excel_data 主键（修正、单条重送时定位行）
         */
        private Long dataId;

        /**
         * 原始Excel行号
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
         * 国家平台错误码（前端按此筛选）
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
    }
}
