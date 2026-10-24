package com.ftcrm.domain;

/**
 * 客户等级。判定规则在 {@code CustomerGradePolicy}，不由接口手填。
 */
public enum CustomerGrade {
    A("高价值"),
    B("有效询盘"),
    C("弱意向"),
    D("待公海");

    private final String label;

    CustomerGrade(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
