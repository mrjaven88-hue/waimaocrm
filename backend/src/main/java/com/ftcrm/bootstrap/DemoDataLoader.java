package com.ftcrm.bootstrap;

import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.security.AuthUser;
import com.ftcrm.service.LeadService;
import com.ftcrm.time.TimeSource;
import com.ftcrm.web.dto.LeadDtos.LeadWriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final LeadService leads;
    private final TimeSource time;

    public DemoDataLoader(AppUserRepository users, PasswordEncoder encoder, LeadService leads, TimeSource time) {
        this.users = users;
        this.encoder = encoder;
        this.leads = leads;
        this.time = time;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByUsername("manager")) {
            return;
        }
        AppUser manager = user("manager", "Manager@123", "陈经理", Role.MANAGER);
        AppUser sales = user("sales", "Sales@123", "林业务", Role.SALESPERSON);
        AuthUser owner = AuthUser.from(sales);
        leads.create(new LeadWriteRequest(
                "汉堡穆勒阀门有限公司",
                "德国",
                "汉斯·穆勒",
                "+4900001112233",
                "hans.mueller@example.com",
                "+4900001112233",
                "不锈钢球阀 DN50",
                "广交会",
                "展会名片，待确认口径"), owner);
        leads.create(new LeadWriteRequest(
                "狮城包装贸易",
                "新加坡",
                "陈美玲",
                "+6500007788",
                "mei.chen@example.com",
                "+6500007788",
                "食品级软管",
                "阿里巴巴",
                null), owner);
        log.info("demo data ready, users={}", users.count());
        if (manager.getId() == null || sales.getId() == null) {
            log.warn("demo users missing ids");
        }
    }

    private AppUser user(String username, String password, String displayName, Role role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setCreatedAt(time.now());
        return users.save(user);
    }
}
