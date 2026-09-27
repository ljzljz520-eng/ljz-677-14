package com.excel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.excel.entity.ExcelData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ExcelDataMapper extends BaseMapper<ExcelData> {

    @Select("SELECT * FROM excel_data WHERE batch_no = #{batchNo} AND report_status = #{status} AND deleted = 0")
    List<ExcelData> selectByBatchAndStatus(@Param("batchNo") String batchNo, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM excel_data WHERE batch_no = #{batchNo} AND deleted = 0")
    Integer countByBatch(@Param("batchNo") String batchNo);

    /**
     * 按错误码分组统计指定批次的异常数量（用于前端筛选项）
     */
    @Select("SELECT error_code AS errorCode, COUNT(*) AS cnt FROM excel_data " +
            "WHERE batch_no = #{batchNo} AND report_status = 2 AND deleted = 0 " +
            "GROUP BY error_code ORDER BY cnt DESC")
    List<Map<String, Object>> countGroupByErrorCode(@Param("batchNo") String batchNo);
}
