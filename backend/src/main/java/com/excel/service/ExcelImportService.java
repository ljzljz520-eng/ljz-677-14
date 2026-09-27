package com.excel.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.User;
import com.excel.listener.ExcelDataListener;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Logger logger = LoggerFactory.getLogger(ExcelImportService.class);

    private final ExcelDataMapper excelDataMapper;
    private final ImportRecordMapper importRecordMapper;
    private final UserMapper userMapper;

    /**
     * 导入Excel文件
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultDTO importExcel(MultipartFile file, Long operatorId) throws IOException {
        String batchNo = IdUtil.fastSimpleUUID();
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();

        logger.info("开始导入Excel文件: {}, 大小: {} bytes, 批次号: {}", fileName, fileSize, batchNo);

        // 获取操作人信息
        User operator = userMapper.selectById(operatorId);
        String operatorName = operator != null ? operator.getRealName() : "系统";

        // 创建导入记录
        ImportRecord record = new ImportRecord();
        record.setBatchNo(batchNo);
        record.setFileName(fileName);
        record.setFileSize(fileSize);
        record.setStatus(0);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        importRecordMapper.insert(record);

        // 使用EasyExcel SAX模式解析，避免OOM
        ExcelDataListener listener = new ExcelDataListener(excelDataMapper, batchNo);

        try {
            // 根据文件后缀判断Excel类型
            ExcelTypeEnum excelType = fileName != null && fileName.endsWith(".xlsx")
                    ? ExcelTypeEnum.XLSX : ExcelTypeEnum.XLS;

            EasyExcel.read(file.getInputStream(), ExcelDataDTO.class, listener)
                    .excelType(excelType)
                    .charset(StandardCharsets.UTF_8)
                    .sheet()
                    .headRowNumber(1)
                    .doRead();

            // 更新导入记录
            record.setTotalCount(listener.getTotalCount());
            record.setSuccessCount(listener.getSuccessCount());
            record.setFailCount(listener.getFailCount());
            record.setStatus(listener.getFailCount() > 0 ? 2 : 1);

            if (!listener.getErrorList().isEmpty()) {
                StringBuilder errorDetails = new StringBuilder();
                for (ExcelDataDTO error : listener.getErrorList()) {
                    errorDetails.append("第").append(error.getRowIndex()).append("行: ")
                            .append(error.getErrorMsg()).append("\n");
                }
                record.setErrorDetails(errorDetails.toString());
            }

            importRecordMapper.updateById(record);

            logger.info("Excel导入完成: 总计{}条，成功{}条，失败{}条",
                    listener.getTotalCount(), listener.getSuccessCount(), listener.getFailCount());

            return ImportResultDTO.builder()
                    .batchNo(batchNo)
                    .totalCount(listener.getTotalCount())
                    .successCount(listener.getSuccessCount())
                    .failCount(listener.getFailCount())
                    .errorList(listener.getErrorList())
                    .status(listener.getFailCount() > 0 ? "completed_with_errors" : "completed")
                    .message(String.format("导入完成，总计%d条，成功%d条，失败%d条",
                            listener.getTotalCount(), listener.getSuccessCount(), listener.getFailCount()))
                    .build();

        } catch (Exception e) {
            logger.error("Excel导入失败", e);
            record.setStatus(2);
            record.setErrorDetails("导入失败: " + e.getMessage());
            importRecordMapper.updateById(record);
            throw new RuntimeException("Excel导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取导入记录列表
     */
    public Page<ImportRecord> getImportRecords(Integer pageNum, Integer pageSize) {
        Page<ImportRecord> page = new Page<>(pageNum, pageSize);
        return importRecordMapper.selectPage(page,
                new LambdaQueryWrapper<ImportRecord>()
                        .orderByDesc(ImportRecord::getCreateTime));
    }

    /**
     * 根据批次号获取数据（支持按上报状态筛选）
     */
    public Page<ExcelData> getDataByBatch(String batchNo, Integer pageNum, Integer pageSize, Integer reportStatus) {
        Page<ExcelData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExcelData> wrapper = new LambdaQueryWrapper<ExcelData>()
                .eq(ExcelData::getBatchNo, batchNo)
                .orderByAsc(ExcelData::getRowNo)
                .orderByAsc(ExcelData::getId);
        if (reportStatus != null) {
            wrapper.eq(ExcelData::getReportStatus, reportStatus);
        }
        return excelDataMapper.selectPage(page, wrapper);
    }

    /**
     * 获取批次按上报状态分组的统计
     */
    public java.util.Map<String, Object> getBatchStats(String batchNo) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        int total = 0, pending = 0, success = 0, failed = 0;
        for (java.util.Map<String, Object> row : excelDataMapper.countGroupByReportStatus(batchNo)) {
            Integer s = ((Number) row.get("reportStatus")).intValue();
            int c = ((Number) row.get("count")).intValue();
            total += c;
            switch (s) {
                case 0 -> pending += c;
                case 1 -> success += c;
                case 2 -> failed += c;
                default -> { }
            }
        }
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("success", success);
        stats.put("failed", failed);
        return stats;
    }

    /**
     * 修正异常行数据：更新业务字段后，将该行状态重置为"待重送(0)"。
     * 异常记录保留在异常列表中直到重送成功，便于用户核对修正进度；
     * 重送时只会发送状态为 0/2 的行，已成功的行不会被重复发送。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExcelData correctData(Long id, com.excel.dto.DataCorrectDTO dto) {
        ExcelData data = excelDataMapper.selectById(id);
        if (data == null) {
            throw new RuntimeException("数据不存在");
        }

        // 复用导入校验规则
        com.excel.dto.ExcelDataDTO validate = new com.excel.dto.ExcelDataDTO();
        validate.setDataCode(data.getDataCode());
        validate.setName(dto.getName() != null ? dto.getName() : data.getName());
        validate.setIdCard(dto.getIdCard());
        validate.setPhone(dto.getPhone());
        validate.setAmount(dto.getAmount());
        validate.setAddress(dto.getAddress());
        validate.setMedicalInsuranceNo(dto.getMedicalInsuranceNo());
        String errorMsg = com.excel.utils.ValidationUtils.validate(validate);
        if (errorMsg != null) {
            throw new RuntimeException("修正后的数据仍不合法: " + errorMsg);
        }

        // 1) 更新业务字段
        excelDataMapper.update(null,
                new LambdaUpdateWrapper<ExcelData>()
                        .eq(ExcelData::getId, id)
                        .set(ExcelData::getName, validate.getName())
                        .set(ExcelData::getIdCard, dto.getIdCard())
                        .set(ExcelData::getMedicalInsuranceNo, dto.getMedicalInsuranceNo())
                        .set(ExcelData::getPhone, dto.getPhone())
                        .set(ExcelData::getAmount, dto.getAmount())
                        .set(ExcelData::getAddress, dto.getAddress())
                        .set(ExcelData::getRemark, dto.getRemark())
        );

        // 2) 仅当该行当前为"上报失败"时，重置为"待重送"，并清除旧错误码
        //    已上报成功的行不受影响，保证不会被重复发送
        boolean wasFailed = Integer.valueOf(2).equals(data.getReportStatus());
        if (wasFailed) {
            excelDataMapper.update(null,
                    new LambdaUpdateWrapper<ExcelData>()
                            .eq(ExcelData::getId, id)
                            .eq(ExcelData::getReportStatus, 2)
                            .set(ExcelData::getReportStatus, 0)
                            .set(ExcelData::getReportMessage, null)
                            .set(ExcelData::getReportErrorCode, null)
                            .set(ExcelData::getReportTime, null)
            );
        }

        return excelDataMapper.selectById(id);
    }

    /**
     * 获取待上报数据
     */
    public List<ExcelData> getPendingReportData(String batchNo) {
        return excelDataMapper.selectByBatchAndStatus(batchNo, 0);
    }

    /**
     * 按ID批量查询数据
     */
    public List<ExcelData> listDataByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>().in(ExcelData::getId, ids)
        );
    }

    /**
     * 按ID查询单行
     */
    public ExcelData getDataById(Long id) {
        return excelDataMapper.selectById(id);
    }

    /**
     * 下载导入模板
     */
    public byte[] downloadTemplate() {
        // 返回模板的字节数组
        return null; // Controller中处理
    }
}
