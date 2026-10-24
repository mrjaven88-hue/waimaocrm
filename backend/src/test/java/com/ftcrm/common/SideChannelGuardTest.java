package com.ftcrm.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SideChannelGuardTest {

    @Test
    void allowsDatesProductCodesAndShortNumbers() {
        assertThat(SideChannelGuard.containsContact("2024-10-23 广交会认识")).isFalse();
        assertThat(SideChannelGuard.containsContact("2024-10-23 10点见面")).isFalse();
        assertThat(SideChannelGuard.containsContact("2024-10-23 15:30")).isFalse();
        assertThat(SideChannelGuard.containsContact("不锈钢球阀 DN50-1234-5678")).isFalse();
        assertThat(SideChannelGuard.containsContact("数量 1200")).isFalse();
        assertThat(SideChannelGuard.containsContact("2024\u201310\u201323")).isFalse();
    }

    @Test
    void rejectsNormalizedPhonesEmailsAndHashFragments() {
        assertThat(SideChannelGuard.containsContact("请打 13800138000")).isTrue();
        assertThat(SideChannelGuard.containsContact("请打 １３８００１３８０００")).isTrue();
        assertThat(SideChannelGuard.containsContact("1234\u200b56789012")).isTrue();
        assertThat(SideChannelGuard.containsContact("电话 +49 000 111 2299")).isTrue();
        assertThat(SideChannelGuard.containsContact("拨打 (415) 555-0133")).isTrue();
        assertThat(SideChannelGuard.containsContact("138-0013-8000")).isTrue();
        assertThat(SideChannelGuard.containsContact("buyer＠example.com")).isTrue();
        assertThat(SideChannelGuard.containsContact("哈希 abcdefabcdefabcd")).isTrue();
        assertThat(SideChannelGuard.containsContact("ABCDEFabcdefABCD")).isTrue();
    }

    @Test
    void rejectionAndDisplayDoNotKeepTheOriginal() {
        assertThatThrownBy(() -> SideChannelGuard.reject("备注 138-0013-8000", "备注"))
                .isInstanceOf(ApiException.class)
                .hasMessage("备注不能包含联系方式");
        assertThat(SideChannelGuard.display("哈希 abcdefabcdefabcd")).isEqualTo("已拦截");
        assertThat(SideChannelGuard.display("展会名片")).isEqualTo("展会名片");
        assertThat(SideChannelGuard.display(null)).isNull();
    }
}
