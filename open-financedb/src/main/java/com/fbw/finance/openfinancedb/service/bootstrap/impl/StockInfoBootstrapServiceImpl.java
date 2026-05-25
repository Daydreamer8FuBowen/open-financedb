package com.fbw.finance.openfinancedb.service.bootstrap.impl;

import com.fbw.finance.openfinancedb.datasource.tushare.TushareReferenceDataSource;
import com.fbw.finance.openfinancedb.model.entity.data.StockInfoEntity;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.service.bootstrap.StockInfoBootstrapService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StockInfoBootstrapServiceImpl implements StockInfoBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(StockInfoBootstrapServiceImpl.class);

    private final TushareReferenceDataSource tushareReferenceDataSource;
    private final StockInfoRepository stockInfoRepository;

    public StockInfoBootstrapServiceImpl(
            TushareReferenceDataSource tushareReferenceDataSource,
            StockInfoRepository stockInfoRepository) {
        this.tushareReferenceDataSource = tushareReferenceDataSource;
        this.stockInfoRepository = stockInfoRepository;
    }

    @Override
    public void refreshFromTushare() {
        log.info("启动装载股票基础信息：开始从 Tushare 获取 stock_basic");
        List<StockInfoEntity> stocks = tushareReferenceDataSource.fetchStockBasicList();
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (StockInfoEntity stock : stocks) {
            try {
                Optional<StockInfoEntity> existing = stockInfoRepository.findBySymbol(stock.getSymbol());
                if (existing.isEmpty()) {
                    stock.setIsRealtimeSyncEnabled(false);
                    if (stockInfoRepository.create(stock) != null) {
                        inserted++;
                    } else {
                        failed++;
                    }
                } else if (baseFieldsChanged(existing.get(), stock)) {
                    stock.setId(existing.get().getId());
                    // 不覆盖用户配置的实时同步开关，只更新 Tushare 维护的基础字段。
                    stock.setIsRealtimeSyncEnabled(existing.get().getIsRealtimeSyncEnabled());
                    if (stockInfoRepository.update(stock)) {
                        updated++;
                    } else {
                        failed++;
                    }
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("启动装载股票基础信息：symbol={} 写入失败，原因={}", stock.getSymbol(), ex.getMessage(), ex);
            }
        }
        log.info("启动装载股票基础信息：完成，Tushare返回={}，新增={}，更新={}，跳过={}，失败={}",
                stocks.size(), inserted, updated, skipped, failed);
    }

    private boolean baseFieldsChanged(StockInfoEntity existing, StockInfoEntity incoming) {
        return !Objects.equals(existing.getRawSymbol(), incoming.getRawSymbol())
                || !Objects.equals(existing.getName(), incoming.getName())
                || !Objects.equals(existing.getExchange(), incoming.getExchange())
                || !Objects.equals(existing.getMarket(), incoming.getMarket())
                || !Objects.equals(existing.getArea(), incoming.getArea())
                || !Objects.equals(existing.getIndustry(), incoming.getIndustry())
                || !Objects.equals(existing.getType(), incoming.getType())
                || !Objects.equals(existing.getListDate(), incoming.getListDate())
                || !Objects.equals(existing.getDelistDate(), incoming.getDelistDate())
                || !Objects.equals(existing.getStatus(), incoming.getStatus())
                || !Objects.equals(existing.getActEntType(), incoming.getActEntType())
                || !Objects.equals(existing.getDataSource(), incoming.getDataSource())
                || !Objects.equals(existing.getLatestQuoteDate(), incoming.getLatestQuoteDate());
    }
}
