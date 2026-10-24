package com.ftcrm.domain;

public enum LeadStage {
    LEAD("线索"),
    INQUIRY("询盘"),
    NEGOTIATION("谈判"),
    WON("成交"),
    SHIPPED("发货"),
    AFTERSALES("售后"),
    LOST("流失");

    private final String label;

    LeadStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
