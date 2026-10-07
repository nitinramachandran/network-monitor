package com.nwmonitor.user;

import com.nwmonitor.common.NwmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Optional headless provisioning: NWM_ROOT_USERNAME / NWM_ROOT_PASSWORD create the root on first start. */
@Component
class RootBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RootBootstrap.class);
    private final UserService users;
    private final NwmProperties props;

    RootBootstrap(UserService users, NwmProperties props) {
        this.users = users;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        var b = props.bootstrap();
        if (b == null || b.rootUsername().isBlank() || b.rootPassword().isBlank()) return;
        if (users.rootExists()) return;
        users.createRoot(b.rootUsername(), "Root", null, b.rootPassword());
        log.info("Created root user '{}' from environment", b.rootUsername());
    }
}
