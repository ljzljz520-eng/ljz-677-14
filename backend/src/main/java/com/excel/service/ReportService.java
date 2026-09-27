package com.excel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.excel.dto.ReportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.ReportError;
import com.excel.enums.NationalErrorCode;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.ReportErrorMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 数据上报服务
 * 模拟数据上报到国家平台，并把平台返回的异常结构化回写：
 * 每条异常包含 行号、医保编号、错误码、错误描述、处理建议。
 * 已上报成功的行不会被重复发送，修正后只重送异常行。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    /**
     * 成功行批量UPDATE的分片大小
     */
    private static final int UPDATE_CHUNK = 1000;

    private final ExcelDataMapper excelDataMapper;
    private final ReportErrorMapper reportErrorMapper;
    private final ImportRecordMapper importRecordMapper;

    /**
     * 上报整个批次中尚未成功的数据（待上报 + 上次失败）。
     * 已上报成功（report_status=1）的行绝不重复发送。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportResultDTO reportToNationalPlatform(String batchNo) {
        logger.info("开始上报数据到国家平台，批次号: {}", batchNo);

        List<ExcelData> pendingList = excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .in(ExcelData::getReportStatus, 0, 2)
                        .orderByAsc(ExcelData::getId)
        );

        return doReport(batchNo, pendingList);
    }

    /**
     * 仅重送指定的异常行（用户修正数据后调用）。
     * 只处理该批次中 上报失败(2) 或 已修正待重送(0) 的行；
     * 已上报成功(1) 的行绝不重复发送。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportResultDTO retryFailedRows(String batchNo, List<Long> dataIds) {
        if (dataIds == null || dataIds.isEmpty()) {
            return emptyResult(batchNo, "没有选择需要重送的异常行");
        }

        List<ExcelData> rows = excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .in(ExcelData::getReportStatus, 0, 2)
                        .in(ExcelData::getId, dataIds)
        );

        if (rows.isEmpty()) {
            return emptyResult(batchNo, "所选行已不是异常状态，无需重送");
        }

        logger.info("修正后重送异常行，批次号: {}，行数: {}", batchNo, rows.size());
        return doReport(batchNo, rows);
    }

    /**
     * 执行上报并把结构化异常写回 report_error 表与导入任务
     */
    private ReportResultDTO doReport(String batchNo, List<ExcelData> rows) {
        if (rows.isEmpty()) {
            return emptyResult(batchNo, "没有待上报的数据");
        }

        int totalCount = rows.size();
        List<Long> successIds = new ArrayList<>();
        List<ReportError> errorEntities = new ArrayList<>();
        List<ReportResultDTO.ReportErrorItem> errorItems = new ArrayList<>();
        List<ExcelData> failedRows = new ArrayList<>();

        Random random = new Random();
        LocalDateTime now = LocalDateTime.now();

        for (ExcelData data : rows) {
            try {
                if (simulateReport(random)) {
                    successIds.add(data.getId());
                } else {
                    NationalErrorCode errorCode = nextErrorCode(random);
                    failedRows.add(data);

                    String errorDesc = "国家平台返回：" + errorCode.getDesc();
                    // 行内保留最新错误码/描述，供列表与筛选使用
                    data.setReportStatus(2);
                    data.setReportMessage(errorDesc);
                    data.setReportErrorCode(errorCode.getCode());

                    // 结构化异常明细（行号、医保编号、错误码、错误描述、处理建议）
                    ReportError error = new ReportError();
                    error.setBatchNo(batchNo);
                    error.setDataId(data.getId());
                    error.setRowNo(data.getRowNo());
                    error.setMedicalInsuranceNo(data.getMedicalInsuranceNo());
                    error.setDataCode(data.getDataCode());
                    error.setName(data.getName());
                    error.setErrorCode(errorCode.getCode());
                    error.setErrorDesc(errorDesc);
                    error.setSuggestion(errorCode.getTip());
                    error.setResolved(0);
                    error.setReportTime(now);
                    errorEntities.add(error);

                    errorItems.add(ReportResultDTO.ReportErrorItem.builder()
                            .dataId(data.getId())
                            .rowNo(data.getRowNo())
                            .medicalInsuranceNo(data.getMedicalInsuranceNo())
                            .dataCode(data.getDataCode())
                            .name(data.getName())
                            .errorCode(errorCode.getCode())
                            .errorDesc(errorDesc)
                            .suggestion(errorCode.getTip())
                            .build());
                }
            } catch (Exception e) {
                logger.error("上报单条数据异常，dataId={}", data.getId(), e);
                failedRows.add(data);
                data.setReportStatus(2);
                data.setReportMessage("系统异常: " + e.getMessage());
                data.setReportErrorCode(NationalErrorCode.E9999.getCode());
            }
        }

        // 1) 成功行：单条SQL按分片批量置成功（已成功行的ID不在集合中，不会被重复发送/覆盖）
        for (int i = 0; i < successIds.size(); i += UPDATE_CHUNK) {
            List<Long> chunk = successIds.subList(i, Math.min(i + UPDATE_CHUNK, successIds.size()));
            excelDataMapper.batchMarkSuccess(chunk, now);
        }

        // 2) 失败行：逐行写入结构化错误码（失败为少数，且每行错误码可能不同）
        for (ExcelData d : failedRows) {
            excelDataMapper.update(null,
                    new LambdaUpdateWrapper<ExcelData>()
                            .eq(ExcelData::getId, d.getId())
                            .set(ExcelData::getReportStatus, 2)
                            .set(ExcelData::getReportMessage, d.getReportMessage())
                            .set(ExcelData::getReportErrorCode, d.getReportErrorCode())
                            .set(ExcelData::getReportTime, now)
            );
        }

        // 3) 异常明细写库：
        //    - 本次重送成功的行，其旧异常置为已处理
        //    - 仍然失败的行，删除其旧的未处理异常后写入最新异常（每个失败行只保留一条未处理记录）
        if (!successIds.isEmpty()) {
            for (int i = 0; i < successIds.size(); i += UPDATE_CHUNK) {
                List<Long> chunk = successIds.subList(i, Math.min(i + UPDATE_CHUNK, successIds.size()));
                reportErrorMapper.update(null,
                        new LambdaUpdateWrapper<ReportError>()
                                .eq(ReportError::getBatchNo, batchNo)
                                .eq(ReportError::getResolved, 0)
                                .in(ReportError::getDataId, chunk)
                                .set(ReportError::getResolved, 1)
                );
            }
        }
        if (!failedRows.isEmpty()) {
            List<Long> failedIds = failedRows.stream().map(ExcelData::getId).toList();
            reportErrorMapper.delete(
                    new LambdaQueryWrapper<ReportError>()
                            .eq(ReportError::getBatchNo, batchNo)
                            .eq(ReportError::getResolved, 0)
                            .in(ReportError::getDataId, failedIds)
            );
        }
        if (!errorEntities.isEmpty()) {
            insertBatchInChunks(errorEntities);
        }

        int successCount = successIds.size();
        int failCount = failedRows.size();

        // 4) 异常结果回写导入任务（批次级统计）
        writeBackImportRecord(batchNo, successCount, failCount);

        String status = failCount == 0 ? "success" : (successCount == 0 ? "failed" : "partial_success");
        String message = String.format("上报完成，本次发送%d条，成功%d条，异常%d条", totalCount, successCount, failCount);
        logger.info(message);

        return ReportResultDTO.builder()
                .batchNo(batchNo)
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failCount)
                .status(status)
                .message(message)
                .errorList(errorItems)
                .build();
    }

    private void writeBackImportRecord(String batchNo, int successCount, int failCount) {
        // 未处理异常总数（可能来自多次上报）
        Long unresolved = reportErrorMapper.selectCount(
                new LambdaQueryWrapper<ReportError>()
                        .eq(ReportError::getBatchNo, batchNo)
                        .eq(ReportError::getResolved, 0)
        );

        // 批次内数据总数 / 已成功数
        int totalData = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>().eq(ExcelData::getBatchNo, batchNo)
        ).intValue();
        Long successData = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 1)
        );

        ImportRecord update = new ImportRecord();
        update.setReportTotalCount(totalData);
        update.setReportSuccessCount(successData.intValue());
        update.setReportFailCount(unresolved.intValue());
        importRecordMapper.update(update,
                new LambdaUpdateWrapper<ImportRecord>().eq(ImportRecord::getBatchNo, batchNo)
        );
    }

    private void insertBatchInChunks(List<ReportError> errors) {
        for (int i = 0; i < errors.size(); i += UPDATE_CHUNK) {
            List<ReportError> chunk = errors.subList(i, Math.min(i + UPDATE_CHUNK, errors.size()));
            reportErrorMapper.insertBatch(chunk);
        }
    }

    /**
     * 模拟上报：5%失败率
     */
    private boolean simulateReport(Random random) {
        return random.nextInt(100) >= 5;
    }

    /**
     * 模拟平台返回的错误码
     */
    private NationalErrorCode nextErrorCode(Random random) {
        NationalErrorCode[] codes = {
                NationalErrorCode.E1001, NationalErrorCode.E1002, NationalErrorCode.E1003,
                NationalErrorCode.E1004, NationalErrorCode.E1005, NationalErrorCode.E1006,
                NationalErrorCode.E1007, NationalErrorCode.E2001, NationalErrorCode.E2002
        };
        return codes[random.nextInt(codes.length)];
    }

    private ReportResultDTO emptyResult(String batchNo, String message) {
        return ReportResultDTO.builder()
                .batchNo(batchNo)
                .totalCount(0)
                .successCount(0)
                .failCount(0)
                .status("no_data")
                .message(message)
                .errorList(Collections.emptyList())
                .build();
    }

    /**
     * 获取批次未处理的上报异常明细（支持按错误码筛选）
     */
    public List<ReportError> getUnresolvedErrors(String batchNo, String errorCode) {
        LambdaQueryWrapper<ReportError> wrapper = new LambdaQueryWrapper<ReportError>()
                .eq(ReportError::getBatchNo, batchNo)
                .eq(ReportError::getResolved, 0)
                .orderByAsc(ReportError::getRowNo);
        if (errorCode != null && !errorCode.isBlank()) {
            wrapper.eq(ReportError::getErrorCode, errorCode);
        }
        return reportErrorMapper.selectList(wrapper);
    }

    /**
     * 错误码维度统计（前端筛选下拉）
     */
    public List<Map<String, Object>> getErrorCodeStats(String batchNo) {
        return reportErrorMapper.countByErrorCode(batchNo);
    }

    /**
     * 兼容旧接口：获取上报失败的数据行
     */
    public List<ExcelData> getFailedReportData(String batchNo) {
        return excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 2)
        );
    }

    /**
     * 兼容旧接口：重置失败行状态为待上报
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetFailedData(String batchNo) {
        excelDataMapper.update(null,
                new LambdaUpdateWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batchNo)
                        .eq(ExcelData::getReportStatus, 2)
                        .set(ExcelData::getReportStatus, 0)
                        .set(ExcelData::getReportMessage, null)
                        .set(ExcelData::getReportErrorCode, null)
                        .set(ExcelData::getReportTime, null)
        );
    }
}
