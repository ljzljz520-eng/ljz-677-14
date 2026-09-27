package com.excel.enums;

import lombok.Getter;

/**
 * 国家平台上报错误码
 * 每个错误码包含：错误码、错误描述、处理建议
 */
@Getter
public enum ReportErrorCode {

    /**
     * 数据格式类错误（1xxx）：需要用户修正数据后重送
     */
    E1001("E1001", "数据格式不符合规范", "请检查该行各字段格式是否符合平台报文规范，修正后重新上报"),
    E1002("E1002", "重复数据已存在", "该医保编号在国家平台已存在记录，请确认是否重复导入；如确认已上报过，无需重复处理"),
    E1003("E1003", "身份证号校验失败", "请核对身份证号为18位且校验位正确，修正后重新上报"),
    E1004("E1004", "手机号格式错误", "请核对手机号为11位有效号码，修正后重新上报"),
    E1005("E1005", "金额超出限额", "请核对金额是否超出平台单笔限额，必要时拆分后重新上报"),

    /**
     * 平台服务类错误（2xxx）：数据本身无问题，稍后直接重试即可
     */
    E2001("E2001", "平台服务暂时不可用", "国家平台临时故障，数据无需修改，请稍后直接重试上报"),
    E2002("E2002", "数据校验超时", "平台处理超时，数据无需修改，请稍后直接重试上报"),

    /**
     * 系统类错误（9xxx）
     */
    E9999("E9999", "系统异常", "系统内部错误，请联系系统管理员处理");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误描述
     */
    private final String description;

    /**
     * 处理建议
     */
    private final String suggestion;

    ReportErrorCode(String code, String description, String suggestion) {
        this.code = code;
        this.description = description;
        this.suggestion = suggestion;
    }

    /**
     * 根据错误码获取枚举，未知错误码返回 E9999
     */
    public static ReportErrorCode fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return E9999;
        }
        for (ReportErrorCode item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return E9999;
    }

    /**
     * 获取处理建议（静态便捷方法，未知错误码返回系统异常建议）
     */
    public static String suggestionOf(String code) {
        return fromCode(code).getSuggestion();
    }
}
