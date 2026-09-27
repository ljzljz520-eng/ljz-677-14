package com.excel.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 上报异常分页查询结果
 * 包含分页异常列表 + 该批次全部错误码统计（供前端筛选下拉使用）
 */
@Data
@Builder
public class ReportErrorPageDTO {

    /**
     * 异常总数（当前筛选条件下）
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 异常明细列表
     */
    private List<ReportErrorItemDTO> list;

    /**
     * 该批次全部错误码统计（不受当前筛选影响，供前端构建筛选项）
     */
    private List<ErrorCodeStatDTO> codeStats;
}
