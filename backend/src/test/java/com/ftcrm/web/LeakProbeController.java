package com.ftcrm.web;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 只存在于测试 classpath，用来证明业务员响应会被明文守卫丢掉。
 */
@RestController
@Profile("test")
public class LeakProbeController {

    @GetMapping("/api/test/leak")
    public Map<String, String> leak() {
        return Map.of("phone", "should-not-pass");
    }

    @GetMapping("/api/test/leak-hash")
    public Map<String, String> leakHash() {
        return Map.of("phone_hash", "abcdefabcdefabcd");
    }

    @GetMapping("/api/test/leak-notes")
    public Map<String, String> leakNotes() {
        return Map.of("notes", "哈希截断 abcdefabcdefabcd");
    }
}
