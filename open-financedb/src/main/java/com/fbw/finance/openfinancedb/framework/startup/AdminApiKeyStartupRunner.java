package com.fbw.finance.openfinancedb.framework.startup;

import com.fbw.finance.openfinancedb.model.entity.apikey.ApiKeyEntity;
import com.fbw.finance.openfinancedb.repository.apikey.ApiKeyRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminApiKeyStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyStartupRunner.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public AdminApiKeyStartupRunner(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (apiKeyRepository.findActiveAdmin().isPresent()) {
            log.info("Admin API key exists, startup generation skipped.");
            return;
        }
        String plainKey = generatePlainKey();
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyName("bootstrap-admin");
        entity.setApiKey(plainKey);
        entity.setIsAdmin(true);
        entity.setStatus(1);
        apiKeyRepository.create(entity);
        log.warn("Generated bootstrap admin API key. Save it now: {}", plainKey);
    }

    private static String generatePlainKey() {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        return "sk-" + HexFormat.of().formatHex(random);
    }

}
