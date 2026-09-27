package com.excel.enums;

import lombok.Getter;

/**
 * 国家平台返回错误码字典
 * code - 平台错误码（前端按此筛选）
 * desc - 错误描述
 * tip  - 处理建议（告诉用户怎么修正后重送）
 */
@Getter
public enum NationalErrorCode {

    E1001("E1001", "数据格式不符合规范", "请按国家平台报文规范检查该行字段格式（编码、日期、枚举值），修正后重送该行"),
    E1002("E1002", "重复数据已存在", "平台已存在相同医保编号的数据，请核实后变更医保编号，或对该行做去重处理后重送"),
    E1003("E1003", "身份证号校验失败", "请核对身份证号位数、出生日期与校验位，修正后重送该行"),
    E1004("E1004", "手机号格式错误", "请填写11位有效手机号后重送该行"),
    E1005("E1005", "金额超出限额", "请核对金额是否超过业务限额，调整到允许范围后重送该行"),
    E1006("E1006", "医保编号不存在", "请核对医保编号是否已在国家平台参保登记，确认编号无误后重送该行"),
    E1007("E1007", "参保状态异常", "该医保编号当前参保状态不允许申报，请核实参保状态后再重送该行"),
    E2001("E2001", "服务暂时不可用", "国家平台临时不可用，无需修改数据，稍后直接重送该行即可"),
    E2002("E2002", "数据校验超时", "平台处理超时，无需修改数据，稍后直接重送该行即可"),
    E9999("E9999", "平台返回未知异常", "请联系系统管理员核对平台报文，确认原因后重送该行");

    private final String code;
    private final String desc;
    private final String tip;

    NationalErrorCode(String code, String desc, String tip) {
        this.code = code;
        this.desc = desc;
        this.tip = tip;
    }

    public static NationalErrorCode byCode(String code) {
        for (NationalErrorCode e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return E9999;
    }
}
