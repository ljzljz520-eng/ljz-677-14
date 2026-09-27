package com.excel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.excel.entity.ExcelData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExcelDataMapper extends BaseMapper<ExcelData> {

    @Select("SELECT * FROM excel_data WHERE batch_no = #{batchNo} AND report_status = #{status} AND deleted = 0")
    List<ExcelData> selectByBatchAndStatus(@Param("batchNo") String batchNo, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM excel_data WHERE batch_no = #{batchNo} AND deleted = 0")
    Integer countByBatch(@Param("batchNo") String batchNo);

    /**
     * 按上报状态统计批次数量
     */
    @Select("""
            SELECT report_status AS reportStatus, COUNT(*) AS count
            FROM excel_data
            WHERE batch_no = #{batchNo} AND deleted = 0
            GROUP BY report_status
            """)
    List<Map<String, Object>> countGroupByReportStatus(@Param("batchNo") String batchNo);

    /**
     * 批量置为上报成功（只针对本次处理的异常行，已上报行不受影响）
     */
    @Update("""
            <script>
            UPDATE excel_data
            SET report_status = 1,
                report_message = '上报成功',
                report_error_code = NULL,
                report_time = #{reportTime}
            WHERE deleted = 0 AND id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int batchMarkSuccess(@Param("ids") List<Long> ids, @Param("reportTime") LocalDateTime reportTime);
}
