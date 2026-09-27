package com.excel.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("import_record")
public class ImportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 总记录数
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
     * 导入状态：0-处理中 1-完成 2-失败
     */
    private Integer status;

    /**
     * 错误信息
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String errorDetails;

    /**
     * 上报总数
     */
    private Integer reportTotalCount;

    /**
     * 上报成功数
     */
    private Integer reportSuccessCount;

    /**
     * 上报失败数（待处理异常数）
     */
    private Integer reportFailCount;

    /**
     * 上报异常明细（JSON数组：行号/医保编号/错误码/错误描述/处理建议）
     * 大字段，列表查询时不返回
     */
    private String reportErrorDetails;

    /**
     * 最近一次上报时间
     */
    private LocalDateTime lastReportTime;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
