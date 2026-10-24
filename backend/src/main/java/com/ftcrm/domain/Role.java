package com.ftcrm.domain;

public enum Role {
    SALESPERSON("业务员"),
    MANAGER("经理");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
