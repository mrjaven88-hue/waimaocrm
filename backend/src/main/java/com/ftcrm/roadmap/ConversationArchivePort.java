package com.ftcrm.roadmap;

/**
 * 阶段⑥沟通存档。只接收密文，不接收明文正文。
 */
public interface ConversationArchivePort {

    void append(long leadId, String channel, String bodyCipher, String bodyIv);
}
