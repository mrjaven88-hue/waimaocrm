package com.ftcrm.roadmap;

/**
 * 阶段⑤公海。连续无有效互动达到 {@link #SILENT_DAYS} 天后进入公海，本阶段不改归属。
 */
public final class PublicPoolPolicy {

    public static final int SILENT_DAYS = 30;

    private PublicPoolPolicy() {
    }
}
