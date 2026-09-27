package com.excel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.excel.dto.DataCorrectDTO;
import com.excel.dto.ReportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.ReportError;
import com.excel.mapper.ExcelDataMapper;
import com.excel.mapper.ImportRecordMapper;
import com.excel.mapper.ReportErrorMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportFlowIntegrationTest {

    @Autowired private ReportService reportService;
    @Autowired private ExcelImportService excelImportService;
    @Autowired private ExcelDataMapper excelDataMapper;
    @Autowired private ReportErrorMapper reportErrorMapper;
    @Autowired private ImportRecordMapper importRecordMapper;

    private void prepareBatch(String batch, int n) {
        ImportRecord record = new ImportRecord();
        record.setBatchNo(batch);
        record.setFileName("it.xlsx");
        record.setTotalCount(n);
        record.setSuccessCount(n);
        record.setFailCount(0);
        record.setStatus(1);
        importRecordMapper.insert(record);

        for (int i = 1; i <= n; i++) {
            ExcelData d = new ExcelData();
            d.setDataCode("D" + i);
            d.setName("姓名" + i);
            d.setMedicalInsuranceNo("YB" + (100000 + i));
            d.setIdCard("11010119900101123" + (i % 10));
            d.setPhone("1380013800" + String.format("%02d", i % 100));
            d.setAddress("测试地址");
            d.setRowNo(i + 1);
            d.setBatchNo(batch);
            d.setReportStatus(0);
            excelDataMapper.insert(d);
        }
    }

    @Test
    void structuredErrorWriteback_filterAndResendOnlyFailedRows() {
        String batch = "BATCH-IT-001";
        prepareBatch(batch, 300);

        // 1) 首次上报
        ReportResultDTO first = reportService.reportToNationalPlatform(batch);
        assertEquals(300, first.getTotalCount());
        assertEquals(300, first.getSuccessCount() + first.getFailCount());
        assertTrue(first.getFailCount() > 0, "5%失败率下300条应至少出现1条异常");

        // 2) 每条异常必须包含 行号/医保编号/错误码/错误描述/处理建议
        for (ReportResultDTO.ReportErrorItem e : first.getErrorList()) {
            assertNotNull(e.getRowNo());
            assertNotNull(e.getMedicalInsuranceNo());
            assertNotNull(e.getErrorCode());
            assertTrue(e.getErrorCode().matches("E\\d{4}"));
            assertNotNull(e.getErrorDesc());
            assertNotNull(e.getSuggestion());
            assertFalse(e.getSuggestion().isBlank());
        }

        // 3) 异常已写回 report_error 表，且 excel_data 行内带错误码
        List<ReportError> persisted = reportService.getUnresolvedErrors(batch, null);
        assertEquals(first.getFailCount(), persisted.size());

        List<ExcelData> failedRows = excelDataMapper.selectList(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batch)
                        .eq(ExcelData::getReportStatus, 2));
        assertEquals(first.getFailCount(), failedRows.size());
        for (ExcelData d : failedRows) {
            assertNotNull(d.getReportErrorCode());
            assertNotNull(d.getReportMessage());
        }

        // 4) 按错误码筛选
        String someCode = persisted.get(0).getErrorCode();
        List<ReportError> filtered = reportService.getUnresolvedErrors(batch, someCode);
        assertTrue(filtered.size() > 0);
        assertTrue(filtered.stream().allMatch(e -> e.getErrorCode().equals(someCode)));

        // 错误码聚合数量之和 == 未处理异常总数
        List<Map<String, Object>> stats = reportService.getErrorCodeStats(batch);
        long sum = stats.stream().mapToLong(m -> ((Number) m.get("count")).longValue()).sum();
        assertEquals(persisted.size(), sum);

        // 5) 导入任务回写
        ImportRecord rec = importRecordMapper.selectOne(
                new LambdaQueryWrapper<ImportRecord>().eq(ImportRecord::getBatchNo, batch));
        assertEquals(300, rec.getReportTotalCount());
        assertEquals(first.getSuccessCount(), rec.getReportSuccessCount());
        assertEquals(first.getFailCount(), rec.getReportFailCount());

        long successBefore = first.getSuccessCount();

        // 6) 修正一条异常行（合法数据）-> 状态必须回到待重送(0)
        ReportError target = persisted.get(0);
        DataCorrectDTO fix = new DataCorrectDTO();
        fix.setName("已修正");
        fix.setMedicalInsuranceNo("YB-FIXED-1");
        fix.setIdCard("110101199002021234");
        fix.setPhone("13900139000");
        fix.setAddress("修正后地址");
        ExcelData fixed = excelImportService.correctData(target.getDataId(), fix);
        assertEquals(0, fixed.getReportStatus());
        assertEquals("YB-FIXED-1", fixed.getMedicalInsuranceNo());
        assertNull(fixed.getReportErrorCode());

        // 7) 只重送选中的异常行（包含刚修正的行）。已成功行不能被重复发送
        List<Long> retryIds = persisted.stream().map(ReportError::getDataId).toList();
        ReportResultDTO second = reportService.retryFailedRows(batch, retryIds);
        assertTrue(second.getTotalCount() <= retryIds.size());
        // 已成功行数量只能增加，绝不应减少（没有重复发送覆盖）
        long successAfter = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batch)
                        .eq(ExcelData::getReportStatus, 1));
        assertTrue(successAfter >= successBefore);

        // 8) 反复只重送异常行（失败2 + 已修正待重送0），最终所有行应都成功；成功行1绝不重复发送
        for (int round = 0; round < 30; round++) {
            List<ExcelData> abnormal = excelDataMapper.selectList(
                    new LambdaQueryWrapper<ExcelData>()
                            .eq(ExcelData::getBatchNo, batch)
                            .in(ExcelData::getReportStatus, 0, 2));
            if (abnormal.isEmpty()) break;
            List<Long> ids = abnormal.stream().map(ExcelData::getId).toList();
            reportService.retryFailedRows(batch, ids);
        }
        long finalFailed = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batch)
                        .eq(ExcelData::getReportStatus, 2));
        long finalPending = excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>()
                        .eq(ExcelData::getBatchNo, batch)
                        .eq(ExcelData::getReportStatus, 0));
        assertEquals(0, finalPending, "不应残留待重送行");
        assertEquals(0, finalFailed, "20轮重送后异常应清零(概率上几乎必然)");
        assertEquals(300, excelDataMapper.selectCount(
                new LambdaQueryWrapper<ExcelData>().eq(ExcelData::getBatchNo, batch)));

        // 9) 全部成功后未处理异常应为0，导入任务异常计数归0
        assertEquals(0, reportService.getUnresolvedErrors(batch, null).size());
        ImportRecord done = importRecordMapper.selectOne(
                new LambdaQueryWrapper<ImportRecord>().eq(ImportRecord::getBatchNo, batch));
        assertEquals(0, done.getReportFailCount());
        assertEquals(300, done.getReportSuccessCount());

        // 10) 此时再整批上报，没有可发送的数据
        ReportResultDTO empty = reportService.reportToNationalPlatform(batch);
        assertEquals(0, empty.getTotalCount());
    }

    @Test
    void invalidCorrectionIsRejected() {
        String batch = "BATCH-IT-002";
        prepareBatch(batch, 200);
        ReportResultDTO main = reportService.reportToNationalPlatform(batch);
        org.junit.jupiter.api.Assumptions.assumeTrue(main.getFailCount() > 0);
        ReportError e = reportService.getUnresolvedErrors(batch, null).get(0);

        DataCorrectDTO bad = new DataCorrectDTO();
        bad.setName(""); // 姓名为空，非法
        assertThrows(RuntimeException.class, () -> excelImportService.correctData(e.getDataId(), bad));
    }
}
