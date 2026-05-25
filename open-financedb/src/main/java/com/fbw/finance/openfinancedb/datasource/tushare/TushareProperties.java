package com.fbw.finance.openfinancedb.datasource.tushare;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finance.tushare")
public class TushareProperties {

    private String httpUrl;
    private String token;
    private Map<String, Integer> qps = new HashMap<>();

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Map<String, Integer> getQps() {
        return qps;
    }

    public void setQps(Map<String, Integer> qps) {
        this.qps = qps;
    }
}
