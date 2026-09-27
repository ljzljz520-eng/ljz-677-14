package com.excel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ErrorCodeStatDTO;
import com.excel.dto.ReportErrorItemDTO;
import com.excel.dto.ReportErrorPageDTO;
import com.excel.dto.ReportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.enums.ReportErrorCode;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 数据上报服务
 * 模拟数据上报到国家平台；平台返回的异常会结构化写回导入任务，
 * 每条异常包含：行号、医保编号（数据编号）、错误码、错误描述、处理建议
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final ExcelDataMapper excelDataMapper;
    private final ImportRecordMapper importRecordMapper;
    /**
     * 使用Spring容器管理的ObjectMapper（已注册JavaTimeModule，可正确序列化LocalDateTime）
     */
    private final ObjectMapper objectMapper;

    /**
     * 上报数据到国家平台
     * 模拟上报过程，可能出现部分失败的情况；
     * 上报完成后将异常结果写回导入任务（import_record）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportResultDTO reportToNationalPlatform(String batchNo) {
        logger.info("开始上报数据到国家平台，批次号: {}", batchNo);

        // 获取待上报数据
        List<ExcelData> pendingList = excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 0)
        );

        if (pendingList.isEmpty()) {
            return ReportResultDTO.builder()
                    .batchNo(batchNo)
                    .totalCount(0)
                    .successCount(0)
                    .failCount(0)
                    .status("no_data")
                    .message("没有待上报的数据")
                    .errorList(new ArrayList<>())
                    .build();
        }

        int totalCount = pendingList.size();
        int successCount = 0;
        int failCount = 0;
        List<ReportResultDTO.ReportErrorItem> errorList = new ArrayList<>();

        Random random = new Random();

        // 模拟上报过程
        for (ExcelData data : pendingList) {
            try {
                // 模拟上报到国家平台，返回null表示成功，否则返回平台错误码
                ReportErrorCode platformError = simulateReport(data, random);

                if (platformError == null) {
                    // 上报成功
                    excelDataMapper.update(null,
                            new LambdaUpdateWrapper<ExcelData>()
                                    .eq(ExcelData::getId, data.getId())
                                    .set(ExcelData::getReportStatus, 1)
                                    .set(ExcelData::getReportMessage, "上报成功")
                                    .set(ExcelData::getErrorCode, null)
                                    .set(ExcelData::getReportTime, LocalDateTime.now())
                    );
                    successCount++;
                } else {
                    // 上报失败：记录错误码 + 错误描述
                    failCount++;
                    markFailed(data, platformError.getCode(), platformError.getDescription());
                    errorList.add(buildErrorItem(data, platformError.getCode(), platformError.getDescription()));
                }
            } catch (Exception e) {
                failCount++;
                String errorMsg = "系统异常: " + e.getMessage();
                markFailed(data, ReportErrorCode.E9999.getCode(), errorMsg);
                errorList.add(buildErrorItem(data, ReportErrorCode.E9999.getCode(), errorMsg));
            }
        }

        // 将异常结果写回导入任务（import_record）
        writeBackReportResult(batchNo);

        String status = failCount == 0 ? "success" : (successCount == 0 ? "failed" : "partial_success");
        String message = String.format("上报完成，总计%d条，成功%d条，失败%d条", totalCount, successCount, failCount);

        logger.info("数据上报完成: {}", message);

        return ReportResultDTO.builder()
                .batchNo(batchNo)
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failCount)
                .status(status)
                .message(message)
                .errorList(errorList)
                .build();
    }

    /**
     * 标记单条数据为上报失败
     */
    private void markFailed(ExcelData data, String errorCode, String errorMsg) {
        excelDataMapper.update(null,
                new LambdaUpdateWrapper<ExcelData>()
                        .eq(ExcelData::getId, data.getId())
                        .set(ExcelData::getReportStatus, 2)
                        .set(ExcelData::getReportMessage, errorMsg)
                        .set(ExcelData::getErrorCode, errorCode)
                        .set(ExcelData::getReportTime, LocalDateTime.now())
        );
    }

    /**
     * 构建异常明细项（含行号、医保编号、错误码、错误描述、处理建议）
     */
    private ReportResultDTO.ReportErrorItem buildErrorItem(ExcelData data, String errorCode, String errorMsg) {
        return ReportResultDTO.ReportErrorItem.builder()
                .id(data.getId())
                .rowIndex(data.getRowIndex())
                .dataCode(data.getDataCode())
                .name(data.getName())
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .suggestion(ReportErrorCode.suggestionOf(errorCode))
                .build();
    }

    /**
     * 将上报结果写回导入任务（import_record）
     * 写入上报统计 + 当前全部未解决异常明细（JSON），每次上报/重试后刷新，
     * 保证导入任务上的异常清单始终反映最新待处理状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void writeBackReportResult(String batchNo) {
        // 统计该批次各状态数量
        Long totalCount = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>().eq(ExcelData::getBatchNo, batchNo));
        Long successCount = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 1));
        Long failCount = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 2));

        // 汇总当前全部失败行，生成异常明细JSON写回导入任务
        String errorDetailsJson = "[]";
        if (failCount > 0) {
            List<ExcelData> failedList = excelDataMapper.selectList(
                    new LambdaQueryWrapper<ExcelData>()
                            .eq(ExcelData::getBatchNo, batchNo)
                            .eq(ExcelData::getReportStatus, 2)
                            .orderByAsc(ExcelData::getRowIndex));
            List<ReportErrorItemDTO> items = failedList.stream()
                    .map(this::toErrorItemDTO)
                    .collect(Collectors.toList());
            try {
                errorDetailsJson = objectMapper.writeValueAsString(items);
            } catch (Exception e) {
                logger.error("异常明细JSON序列化失败，批次号: {}", batchNo, e);
            }
        }

        importRecordMapper.update(null,
                new LambdaUpdateWrapper<ImportRecord>()
                        .eq(ImportRecord::getBatchNo, batchNo)
                        .set(ImportRecord::getReportTotalCount, totalCount.intValue())
                        .set(ImportRecord::getReportSuccessCount, successCount.intValue())
                        .set(ImportRecord::getReportFailCount, failCount.intValue())
                        .set(ImportRecord::getReportErrorDetails, errorDetailsJson)
                        .set(ImportRecord::getLastReportTime, LocalDateTime.now())
        );

        logger.info("异常结果已写回导入任务，批次号: {}，待处理异常: {}条", batchNo, failCount);
    }

    /**
     * 分页查询上报异常明细，支持按错误码筛选
     * 用户无需在几万条数据中手工查找问题
     */
    public ReportErrorPageDTO getReportErrors(String batchNo, String errorCode, Integer pageNum, Integer pageSize) {
        Page<ExcelData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExcelData> wrapper = new LambdaQueryWrapper<ExcelData>()
                .eq(ExcelData::getBatchNo, batchNo)
                .eq(ExcelData::getReportStatus, 2)
                .orderByAsc(ExcelData::getRowIndex);
        if (errorCode != null && !errorCode.isEmpty()) {
            wrapper.eq(ExcelData::getErrorCode, errorCode);
        }

        Page<ExcelData> result = excelDataMapper.selectPage(page, wrapper);

        List<ReportErrorItemDTO> items = result.getRecords().stream()
                .map(this::toErrorItemDTO)
                .collect(Collectors.toList());

        return ReportErrorPageDTO.builder()
                .total(result.getTotal())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .list(items)
                .codeStats(getErrorCodeStats(batchNo))
                .build();
    }

    /**
     * 获取指定批次的错误码统计（各错误码的异常数量），供前端构建筛选项
     */
    public List<ErrorCodeStatDTO> getErrorCodeStats(String batchNo) {
        List<Map<String, Object>> rows = excelDataMapper.countGroupByErrorCode(batchNo);
        List<ErrorCodeStatDTO> stats = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("errorCode");
            Number cnt = (Number) row.get("cnt");
            ReportErrorCode ec = ReportErrorCode.fromCode(code);
            stats.add(ErrorCodeStatDTO.builder()
                    .errorCode(ec.getCode())
                    .errorMsg(ec.getDescription())
                    .suggestion(ec.getSuggestion())
                    .count(cnt != null ? cnt.longValue() : 0L)
                    .build());
        }
        return stats;
    }

    /**
     * 实体转异常明细DTO（补充处理建议，携带完整业务字段便于前端回显修正）
     */
    private ReportErrorItemDTO toErrorItemDTO(ExcelData data) {
        return ReportErrorItemDTO.builder()
                .id(data.getId())
                .rowIndex(data.getRowIndex())
                .dataCode(data.getDataCode())
                .name(data.getName())
                .idCard(data.getIdCard())
                .phone(data.getPhone())
                .amount(data.getAmount())
                .address(data.getAddress())
                .remark(data.getRemark())
                .errorCode(data.getErrorCode())
                .errorMsg(data.getReportMessage())
                .suggestion(ReportErrorCode.suggestionOf(data.getErrorCode()))
                .reportTime(data.getReportTime())
                .build();
    }

    /**
     * 模拟上报过程
     * 模拟可能的失败情况，返回null表示成功，否则返回平台错误码
     */
    private ReportErrorCode simulateReport(ExcelData data, Random random) {
        // 模拟5%的失败率
        if (random.nextInt(100) < 5) {
            return generatePlatformError(random);
        }
        return null;
    }

    /**
     * 生成模拟平台错误码
     * 数据格式类错误（需用户修正）与平台服务类错误（直接重试即可）按权重返回
     */
    private ReportErrorCode generatePlatformError(Random random) {
        ReportErrorCode[] dataErrors = {
                ReportErrorCode.E1001, ReportErrorCode.E1002, ReportErrorCode.E1003,
                ReportErrorCode.E1004, ReportErrorCode.E1005
        };
        ReportErrorCode[] serviceErrors = {
                ReportErrorCode.E2001, ReportErrorCode.E2002
        };
        // 70%为数据类错误，30%为平台服务类错误
        if (random.nextInt(100) < 70) {
            return dataErrors[random.nextInt(dataErrors.length)];
        }
        return serviceErrors[random.nextInt(serviceErrors.length)];
    }

    /**
     * 获取上报失败的数据
     */
    public List<ExcelData> getFailedReportData(String batchNo) {
        return excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 2)
                        .orderByAsc(ExcelData::getRowIndex)
        );
    }

    /**
     * 重置失败数据状态（仅异常行），重送时只会发送这些行
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetFailedData(String batchNo) {
        excelDataMapper.update(null,
                new LambdaUpdateWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 2)
                        .set(ExcelData::getReportStatus, 0)
                        .set(ExcelData::getReportMessage, null)
                        .set(ExcelData::getErrorCode, null)
                        .set(ExcelData::getReportTime, null)
        );
    }
}
