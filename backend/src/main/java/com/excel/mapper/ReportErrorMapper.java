package com.excel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.excel.entity.ReportError;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportErrorMapper extends BaseMapper<ReportError> {

    /**
     * 按批次 + 错误码统计未处理异常数量（供前端错误码筛选下拉）
     */
    @Select("""
            SELECT error_code AS errorCode, COUNT(*) AS count
            FROM report_error
            WHERE batch_no = #{batchNo} AND resolved = 0 AND deleted = 0
            GROUP BY error_code
            ORDER BY count DESC
            """)
    List<Map<String, Object>> countByErrorCode(@Param("batchNo") String batchNo);

    /**
     * 批量插入异常明细（单条多值INSERT，避免数万次单插）
     */
    @Insert("""
            <script>
            INSERT INTO report_error
                (batch_no, data_id, row_no, medical_insurance_no, data_code, name,
                 error_code, error_desc, suggestion, resolved, report_time)
            VALUES
            <foreach collection='list' item='e' separator=','>
                (#{e.batchNo}, #{e.dataId}, #{e.rowNo}, #{e.medicalInsuranceNo},
                 #{e.dataCode}, #{e.name}, #{e.errorCode}, #{e.errorDesc},
                 #{e.suggestion}, #{e.resolved}, #{e.reportTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<ReportError> list);
}
