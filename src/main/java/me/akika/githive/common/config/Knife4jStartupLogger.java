package me.akika.githive.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Knife4jStartupLogger {

    private final Environment environment;

    public Knife4jStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logKnife4jUrl() {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
        String contextPath = StringUtils.defaultIfBlank(
                environment.getProperty("server.servlet.context-path"), ""
        );
        String normalizedContextPath = "/".equals(contextPath) ? "" : contextPath;

        log.info("Knife4j 文档地址: http://localhost:{}{}/doc.html", port, normalizedContextPath);
        log.info("OpenAPI 地址: http://localhost:{}{}/v3/api-docs", port, normalizedContextPath);
    }
}
