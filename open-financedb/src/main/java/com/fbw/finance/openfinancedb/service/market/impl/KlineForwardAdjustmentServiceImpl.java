package com.fbw.finance.openfinancedb.service.market.impl;

import com.fbw.finance.openfinancedb.framework.exception.ErrorCodeConstants;
import com.fbw.finance.openfinancedb.framework.exception.ServiceException;
import com.fbw.finance.openfinancedb.model.entity.data.StockSyncStateEntity;
import com.fbw.finance.openfinancedb.model.enums.SyncDataType;
import com.fbw.finance.openfinancedb.model.enums.SyncStatus;
import com.fbw.finance.openfinancedb.model.market.AdjFactorPoint;
import com.fbw.finance.openfinancedb.model.market.KlineBar;
import com.fbw.finance.openfinancedb.model.market.KlineQuery;
import com.fbw.finance.openfinancedb.repository.data.StockInfoRepository;
import com.fbw.finance.openfinancedb.repository.data.StockSyncStateRepository;
import com.fbw.finance.openfinancedb.repository.market.AdjFactorRepository;
import com.fbw.finance.openfinancedb.service.market.KlineForwardAdjustmentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class KlineForwardAdjustmentServiceImpl implements KlineForwardAdjustmentService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PRICE_SCALE = 6;
    private static final int RATIO_SCALE = 12;

    private final AdjFactorRepository adjFactorRepository;
    private final StockInfoRepository stockInfoRepository;
    private final StockSyncStateRepository stockSyncStateRepository;

    public KlineForwardAdjustmentServiceImpl(
            AdjFactorRepository adjFactorRepository,
            StockInfoRepository stockInfoRepository,
            StockSyncStateRepository stockSyncStateRepository) {
        this.adjFactorRepository = adjFactorRepository;
        this.stockInfoRepository = stockInfoRepository;
        this.stockSyncStateRepository = stockSyncStateRepository;
    }

    @Override
    public List<KlineBar> forwardAdjust(KlineQuery query, List<KlineBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        ensureAdjustmentDataAvailable(query.symbol());

        LocalDate startDate = bars.stream()
                .map(this::tradeDate)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate endDate = bars.stream()
                .map(this::tradeDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
        NavigableMap<LocalDate, BigDecimal> factors = loadFactors(query.symbol(), startDate, endDate);
        BigDecimal latestFactor = factorAtOrBefore(factors, endDate, query.symbol());

        return bars.stream()
                .sorted(Comparator.comparing(KlineBar::time))
                .map(bar -> adjustBar(bar, factorAtOrBefore(factors, tradeDate(bar), query.symbol()), latestFactor))
                .toList();
    }

    private void ensureAdjustmentDataAvailable(String symbol) {
        boolean syncEnabled = stockInfoRepository.findBySymbol(symbol)
                .filter(stock -> Boolean.TRUE.equals(stock.getIsRealtimeSyncEnabled()))
                .isPresent();
        if (!syncEnabled || !syncStateCompleted(symbol, SyncDataType.ADJ_FACTOR)) {
            throw new ServiceException(
                    ErrorCodeConstants.KLINE_DATA_INCOMPLETE,
                    "adjustment factors are not ready for symbol: " + symbol
            );
        }
    }

    private boolean syncStateCompleted(String symbol, SyncDataType dataType) {
        return stockSyncStateRepository.findBySymbolAndDataType(symbol, dataType.getCode())
                .filter(state -> SyncStatus.SUCCESS.getCode().equals(state.getSyncStatus()))
                .isPresent();
    }

    private NavigableMap<LocalDate, BigDecimal> loadFactors(String symbol, LocalDate startDate, LocalDate endDate) {
        NavigableMap<LocalDate, BigDecimal> factors = new TreeMap<>();
        for (AdjFactorPoint factor : adjFactorRepository.query(symbol, startDate, endDate)) {
            factors.put(factor.tradeDate(), factor.adjFactor());
        }
        if (factors.isEmpty()) {
            throw new ServiceException(
                    ErrorCodeConstants.KLINE_DATA_INCOMPLETE,
                    "adjustment factors are missing for symbol: " + symbol
            );
        }
        return factors;
    }

    private BigDecimal factorAtOrBefore(NavigableMap<LocalDate, BigDecimal> factors, LocalDate tradeDate, String symbol) {
        var entry = factors.floorEntry(tradeDate);
        if (entry == null || entry.getValue() == null || BigDecimal.ZERO.compareTo(entry.getValue()) == 0) {
            throw new ServiceException(
                    ErrorCodeConstants.KLINE_DATA_INCOMPLETE,
                    "adjustment factor is missing for symbol: " + symbol + ", tradeDate: " + tradeDate
            );
        }
        return entry.getValue();
    }

    private KlineBar adjustBar(KlineBar bar, BigDecimal barFactor, BigDecimal latestFactor) {
        BigDecimal ratio = barFactor.divide(latestFactor, RATIO_SCALE, RoundingMode.HALF_UP);
        return new KlineBar(
                bar.symbol(),
                bar.period(),
                bar.time(),
                adjustPrice(bar.open(), ratio),
                adjustPrice(bar.high(), ratio),
                adjustPrice(bar.low(), ratio),
                adjustPrice(bar.close(), ratio),
                bar.volume(),
                bar.amount(),
                bar.complete(),
                bar.source()
        );
    }

    private BigDecimal adjustPrice(BigDecimal price, BigDecimal ratio) {
        if (price == null) {
            return null;
        }
        return price.multiply(ratio).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate tradeDate(KlineBar bar) {
        return bar.time().atZone(MARKET_ZONE).toLocalDate();
    }
}
