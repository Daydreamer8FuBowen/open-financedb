package com.fbw.finance.openfinancedb.framework.startup;

import com.fbw.finance.openfinancedb.service.bootstrap.StockInfoBootstrapService;
import com.fbw.finance.openfinancedb.service.bootstrap.TradeCalendarBootstrapService;
import com.fbw.finance.openfinancedb.service.market.KlineAggregationWorker;
import com.fbw.finance.openfinancedb.service.market.HistoryKlineSyncWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MarketDataStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketDataStartupRunner.class);

    private final StockInfoBootstrapService stockInfoBootstrapService;
    private final TradeCalendarBootstrapService tradeCalendarBootstrapService;
    private final HistoryKlineSyncWorker historyKlineSyncWorker;
    private final KlineAggregationWorker klineAggregationWorker;
    private final boolean bootstrapEnabled;
    private final boolean historySyncEnabled;
    private final boolean klineAggregationEnabled;

    public MarketDataStartupRunner(
            StockInfoBootstrapService stockInfoBootstrapService,
            TradeCalendarBootstrapService tradeCalendarBootstrapService,
            HistoryKlineSyncWorker historyKlineSyncWorker,
            KlineAggregationWorker klineAggregationWorker,
            @Value("${finance.startup.bootstrap-enabled:true}") boolean bootstrapEnabled,
            @Value("${finance.history-sync.enabled:true}") boolean historySyncEnabled,
            @Value("${finance.kline-aggregation.enabled:true}") boolean klineAggregationEnabled) {
        this.stockInfoBootstrapService = stockInfoBootstrapService;
        this.tradeCalendarBootstrapService = tradeCalendarBootstrapService;
        this.historyKlineSyncWorker = historyKlineSyncWorker;
        this.klineAggregationWorker = klineAggregationWorker;
        this.bootstrapEnabled = bootstrapEnabled;
        this.historySyncEnabled = historySyncEnabled;
        this.klineAggregationEnabled = klineAggregationEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEnabled) {
            log.info("市场数据启动流程：开始执行基础数据装载");
            stockInfoBootstrapService.refreshFromTushare();
            tradeCalendarBootstrapService.initializeIfEmpty();
            log.info("市场数据启动流程：基础数据装载完成");
        } else {
            log.info("市场数据启动流程：基础数据装载已关闭，跳过 stock_info 和 trade_calendar 初始化");
        }

        if (historySyncEnabled) {
            log.info("市场数据启动流程：启动历史分钟线后台同步线程");
            historyKlineSyncWorker.start();
        } else {
            log.info("市场数据启动流程：历史分钟线后台同步已关闭");
        }

        if (klineAggregationEnabled) {
            log.info("市场数据启动流程：启动多周期 K 线后台聚合线程池");
            klineAggregationWorker.start();
        } else {
            log.info("市场数据启动流程：多周期 K 线后台聚合已关闭");
        }
    }
}
