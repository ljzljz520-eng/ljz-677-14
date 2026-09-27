package com.excel.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.excel.dto.ApiResponse;
import com.excel.dto.DataCorrectDTO;
import com.excel.dto.ErrorExportDTO;
import com.excel.dto.ExcelDataDTO;
import com.excel.dto.ImportResultDTO;
import com.excel.dto.ReportResultDTO;
import com.excel.entity.ExcelData;
import com.excel.entity.ImportRecord;
import com.excel.entity.ReportError;
import com.excel.enums.NationalErrorCode;
import com.excel.service.ExcelImportService;
import com.excel.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
@Tag(name = "Excel导入管理", description = "Excel数据导入与上报接口")
public class ExcelController {

    private static final Logger logger = LoggerFactory.getLogger(ExcelController.class);

    private final ExcelImportService excelImportService;
    private final ReportService reportService;

    @PostMapping("/import")
    @Operation(summary = "导入Excel", description = "上传Excel文件进行数据导入")
    public ApiResponse<ImportResultDTO> importExcel(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            if (file.isEmpty()) {
                return ApiResponse.error("请选择要上传的文件");
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                return ApiResponse.error("仅支持Excel文件（.xlsx或.xls）");
            }

            Long userId = (Long) authentication.getPrincipal();
            ImportResultDTO result = excelImportService.importExcel(file, userId);
            return ApiResponse.success("导入完成", result);
        } catch (Exception e) {
            logger.error("Excel导入失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/records")
    @Operation(summary = "获取导入记录", description = "分页获取导入记录列表")
    public ApiResponse<Page<ImportRecord>> getImportRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ImportRecord> page = excelImportService.getImportRecords(pageNum, pageSize);
        return ApiResponse.success(page);
    }

    @GetMapping("/data/{batchNo}")
    @Operation(summary = "获取批次数据", description = "根据批次号分页获取数据，可按上报状态筛选")
    public ApiResponse<Page<ExcelData>> getDataByBatch(
            @PathVariable String batchNo,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer reportStatus) {
        Page<ExcelData> page = excelImportService.getDataByBatch(batchNo, pageNum, pageSize, reportStatus);
        return ApiResponse.success(page);
    }

    @GetMapping("/stats/{batchNo}")
    @Operation(summary = "批次上报统计", description = "总数/待上报/已上报/异常数")
    public ApiResponse<Map<String, Object>> getBatchStats(@PathVariable String batchNo) {
        return ApiResponse.success(excelImportService.getBatchStats(batchNo));
    }

    @GetMapping("/row/{id}")
    @Operation(summary = "获取单行数据", description = "按主键获取一条数据（修正弹窗回填用）")
    public ApiResponse<ExcelData> getRow(@PathVariable Long id) {
        ExcelData data = excelImportService.getDataById(id);
        if (data == null) {
            return ApiResponse.error("数据不存在");
        }
        return ApiResponse.success(data);
    }

    @PutMapping("/data/correct/{id}")
    @Operation(summary = "修正异常行", description = "在线修正异常数据，修正后该行变为待重送状态")
    public ApiResponse<ExcelData> correctData(@PathVariable Long id, @RequestBody DataCorrectDTO dto) {
        try {
            ExcelData data = excelImportService.correctData(id, dto);
            return ApiResponse.success("修正成功，该行已加入待重送列表", data);
        } catch (Exception e) {
            logger.error("数据修正失败, id={}", id, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/report/{batchNo}")
    @Operation(summary = "上报数据", description = "上报批次中尚未成功的数据（已上报成功的不会重复发送）")
    public ApiResponse<ReportResultDTO> reportData(@PathVariable String batchNo) {
        try {
            ReportResultDTO result = reportService.reportToNationalPlatform(batchNo);
            return ApiResponse.success("上报完成", result);
        } catch (Exception e) {
            logger.error("数据上报失败", e);
            return ApiResponse.error("上报失败: " + e.getMessage());
        }
    }

    @PostMapping("/report/retry/{batchNo}")
    @Operation(summary = "重试上报", description = "重送批次中所有未处理的异常行（已上报成功的不重复发送）")
    public ApiResponse<ReportResultDTO> retryReport(@PathVariable String batchNo) {
        try {
            ReportResultDTO result = reportService.reportToNationalPlatform(batchNo);
            return ApiResponse.success("重新上报完成", result);
        } catch (Exception e) {
            logger.error("重新上报失败", e);
            return ApiResponse.error("重新上报失败: " + e.getMessage());
        }
    }

    @PostMapping("/report/retry-rows/{batchNo}")
    @Operation(summary = "重送指定异常行", description = "用户修正后，只重送勾选的异常行")
    public ApiResponse<ReportResultDTO> retryRows(
            @PathVariable String batchNo,
            @RequestBody Map<String, List<Long>> body) {
        try {
            List<Long> ids = body.getOrDefault("ids", new ArrayList<>());
            ReportResultDTO result = reportService.retryFailedRows(batchNo, ids);
            return ApiResponse.success("重送完成", result);
        } catch (Exception e) {
            logger.error("重送异常行失败", e);
            return ApiResponse.error("重送失败: " + e.getMessage());
        }
    }

    @GetMapping("/report/errors/{batchNo}")
    @Operation(summary = "异常明细分页", description = "获取结构化上报异常，可按错误码筛选")
    public ApiResponse<Page<ReportError>> getReportErrors(
            @PathVariable String batchNo,
            @RequestParam(required = false) String errorCode,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        List<ReportError> all = reportService.getUnresolvedErrors(batchNo, errorCode);
        Page<ReportError> page = new Page<>(pageNum, pageSize, all.size());
        int from = Math.min((pageNum - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        page.setRecords(all.subList(from, to));
        return ApiResponse.success(page);
    }

    @GetMapping("/report/error-codes/{batchNo}")
    @Operation(summary = "错误码聚合", description = "按错误码统计未处理异常数量，供前端筛选")
    public ApiResponse<List<Map<String, Object>>> getErrorCodeStats(@PathVariable String batchNo) {
        return ApiResponse.success(reportService.getErrorCodeStats(batchNo));
    }

    @GetMapping("/error-code-dict")
    @Operation(summary = "错误码字典", description = "错误码、描述与处理建议")
    public ApiResponse<List<Map<String, String>>> getErrorCodeDict() {
        List<Map<String, String>> list = new ArrayList<>();
        for (NationalErrorCode e : NationalErrorCode.values()) {
            list.add(Map.of("code", e.getCode(), "desc", e.getDesc(), "suggestion", e.getTip()));
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/report/failed/{batchNo}")
    @Operation(summary = "获取上报失败数据", description = "获取指定批次上报失败的数据")
    public ApiResponse<List<ExcelData>> getFailedReportData(@PathVariable String batchNo) {
        List<ExcelData> failedList = reportService.getFailedReportData(batchNo);
        return ApiResponse.success(failedList);
    }

    @GetMapping("/template")
    @Operation(summary = "下载导入模板", description = "下载Excel导入模板")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("数据导入模板", StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-''" + fileName + ".xlsx");

        // 生成模板数据
        List<ExcelDataDTO> templateData = new ArrayList<>();
        ExcelDataDTO example = new ExcelDataDTO();
        example.setDataCode("DATA001");
        example.setName("张三");
        example.setIdCard("110101199001011234");
        example.setPhone("13800138000");
        example.setAmount(new BigDecimal("1000.00"));
        example.setAddress("北京市朝阳区xxx街道");
        example.setRemark("示例数据");
        example.setMedicalInsuranceNo("YB202400001");
        templateData.add(example);

        EasyExcel.write(response.getOutputStream(), ExcelDataDTO.class)
                .sheet("数据导入模板")
                .doWrite(templateData);
    }

    @GetMapping("/export/errors/{batchNo}")
    @Operation(summary = "导出异常数据", description = "导出未处理的上报异常（含行号/医保编号/错误码/描述/建议），可按错误码导出")
    public void exportErrors(
            @PathVariable String batchNo,
            @RequestParam(required = false) String errorCode,
            HttpServletResponse response) throws IOException {
        List<ReportError> errors = reportService.getUnresolvedErrors(batchNo, errorCode);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("国家平台上报异常_" + batchNo, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-''" + fileName + ".xlsx");

        List<ErrorExportDTO> exportList = new ArrayList<>();
        if (!errors.isEmpty()) {
            List<Long> dataIds = errors.stream().map(ReportError::getDataId).toList();
            Map<Long, ExcelData> dataMap = excelImportService.listDataByIds(dataIds).stream()
                    .collect(java.util.stream.Collectors.toMap(ExcelData::getId, d -> d, (a, b) -> a));
            for (ReportError e : errors) {
                ErrorExportDTO dto = new ErrorExportDTO();
                dto.setRowNo(e.getRowNo());
                dto.setMedicalInsuranceNo(e.getMedicalInsuranceNo());
                dto.setDataCode(e.getDataCode());
                dto.setName(e.getName());
                dto.setErrorCode(e.getErrorCode());
                dto.setErrorDesc(e.getErrorDesc());
                dto.setSuggestion(e.getSuggestion());

                ExcelData d = dataMap.get(e.getDataId());
                if (d != null) {
                    dto.setIdCard(d.getIdCard());
                    dto.setPhone(d.getPhone());
                    dto.setAmount(d.getAmount());
                    dto.setAddress(d.getAddress());
                    dto.setRemark(d.getRemark());
                }
                exportList.add(dto);
            }
        }

        EasyExcel.write(response.getOutputStream(), ErrorExportDTO.class)
                .sheet("上报异常明细")
                .doWrite(exportList);
    }
}
