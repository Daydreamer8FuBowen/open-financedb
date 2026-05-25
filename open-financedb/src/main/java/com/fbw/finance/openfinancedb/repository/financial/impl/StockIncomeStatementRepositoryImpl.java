package com.fbw.finance.openfinancedb.repository.financial.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fbw.finance.openfinancedb.model.entity.financial.StockIncomeStatementEntity;
import com.fbw.finance.openfinancedb.repository.financial.StockIncomeStatementRepository;
import com.fbw.finance.openfinancedb.repository.financial.mapper.StockIncomeStatementMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StockIncomeStatementRepositoryImpl implements StockIncomeStatementRepository {

    private final StockIncomeStatementMapper stockIncomeStatementMapper;

    public StockIncomeStatementRepositoryImpl(StockIncomeStatementMapper stockIncomeStatementMapper) {
        this.stockIncomeStatementMapper = stockIncomeStatementMapper;
    }

    @Override
    public Long create(StockIncomeStatementEntity entity) {
        return stockIncomeStatementMapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(StockIncomeStatementEntity entity) {
        LambdaUpdateWrapper<StockIncomeStatementEntity> updateWrapper = baseUpdateWrapper(entity)
                .eq(StockIncomeStatementEntity::getId, entity.getId());
        return stockIncomeStatementMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean upsertByUniqueKey(StockIncomeStatementEntity entity) {
        Optional<StockIncomeStatementEntity> existing = findByUniqueKey(
                entity.getSymbol(),
                entity.getEndDate(),
                entity.getReportType(),
                entity.getCompType()
        );
        if (existing.isEmpty()) {
            return create(entity) != null;
        }
        entity.setId(existing.get().getId());
        return update(entity);
    }

    @Override
    public Optional<StockIncomeStatementEntity> findByUniqueKey(
            String symbol,
            LocalDate endDate,
            String reportType,
            String compType) {
        LambdaQueryWrapper<StockIncomeStatementEntity> queryWrapper = new LambdaQueryWrapper<StockIncomeStatementEntity>()
                .eq(StockIncomeStatementEntity::getSymbol, symbol)
                .eq(StockIncomeStatementEntity::getEndDate, endDate)
                .eq(StockIncomeStatementEntity::getReportType, reportType)
                .eq(StockIncomeStatementEntity::getCompType, compType);
        return Optional.ofNullable(stockIncomeStatementMapper.selectOne(queryWrapper));
    }

    @Override
    public List<StockIncomeStatementEntity> findBySymbolAndEndDateBetween(
            String symbol,
            LocalDate startDate,
            LocalDate endDate) {
        LambdaQueryWrapper<StockIncomeStatementEntity> queryWrapper = new LambdaQueryWrapper<StockIncomeStatementEntity>()
                .eq(StockIncomeStatementEntity::getSymbol, symbol)
                .ge(StockIncomeStatementEntity::getEndDate, startDate)
                .le(StockIncomeStatementEntity::getEndDate, endDate)
                .orderByAsc(StockIncomeStatementEntity::getEndDate);
        return stockIncomeStatementMapper.selectList(queryWrapper);
    }

    private LambdaUpdateWrapper<StockIncomeStatementEntity> baseUpdateWrapper(StockIncomeStatementEntity entity) {
        return new LambdaUpdateWrapper<StockIncomeStatementEntity>()
                .set(StockIncomeStatementEntity::getSymbol, entity.getSymbol())
                .set(StockIncomeStatementEntity::getAnnDate, entity.getAnnDate())
                .set(StockIncomeStatementEntity::getFAnnDate, entity.getFAnnDate())
                .set(StockIncomeStatementEntity::getEndDate, entity.getEndDate())
                .set(StockIncomeStatementEntity::getReportType, entity.getReportType())
                .set(StockIncomeStatementEntity::getCompType, entity.getCompType())
                .set(StockIncomeStatementEntity::getEndType, entity.getEndType())
                .set(StockIncomeStatementEntity::getBasicEps, entity.getBasicEps())
                .set(StockIncomeStatementEntity::getDilutedEps, entity.getDilutedEps())
                .set(StockIncomeStatementEntity::getTotalRevenue, entity.getTotalRevenue())
                .set(StockIncomeStatementEntity::getRevenue, entity.getRevenue())
                .set(StockIncomeStatementEntity::getIntIncome, entity.getIntIncome())
                .set(StockIncomeStatementEntity::getCommIncome, entity.getCommIncome())
                .set(StockIncomeStatementEntity::getNCommisIncome, entity.getNCommisIncome())
                .set(StockIncomeStatementEntity::getNOthIncome, entity.getNOthIncome())
                .set(StockIncomeStatementEntity::getNOthBIncome, entity.getNOthBIncome())
                .set(StockIncomeStatementEntity::getOthBIncome, entity.getOthBIncome())
                .set(StockIncomeStatementEntity::getFvValueChgGain, entity.getFvValueChgGain())
                .set(StockIncomeStatementEntity::getInvestIncome, entity.getInvestIncome())
                .set(StockIncomeStatementEntity::getForexGain, entity.getForexGain())
                .set(StockIncomeStatementEntity::getTotalCogs, entity.getTotalCogs())
                .set(StockIncomeStatementEntity::getIntExp, entity.getIntExp())
                .set(StockIncomeStatementEntity::getCommExp, entity.getCommExp())
                .set(StockIncomeStatementEntity::getBizTaxSurchg, entity.getBizTaxSurchg())
                .set(StockIncomeStatementEntity::getAdminExp, entity.getAdminExp())
                .set(StockIncomeStatementEntity::getOperExp, entity.getOperExp())
                .set(StockIncomeStatementEntity::getOperateProfit, entity.getOperateProfit())
                .set(StockIncomeStatementEntity::getNonOperIncome, entity.getNonOperIncome())
                .set(StockIncomeStatementEntity::getNonOperExp, entity.getNonOperExp())
                .set(StockIncomeStatementEntity::getTotalProfit, entity.getTotalProfit())
                .set(StockIncomeStatementEntity::getIncomeTax, entity.getIncomeTax())
                .set(StockIncomeStatementEntity::getNetIncome, entity.getNetIncome())
                .set(StockIncomeStatementEntity::getNIncomeAttrP, entity.getNIncomeAttrP())
                .set(StockIncomeStatementEntity::getOthComprIncome, entity.getOthComprIncome())
                .set(StockIncomeStatementEntity::getTComprIncome, entity.getTComprIncome())
                .set(StockIncomeStatementEntity::getComprIncAttrP, entity.getComprIncAttrP())
                .set(StockIncomeStatementEntity::getContinuedNetProfit, entity.getContinuedNetProfit())
                .set(StockIncomeStatementEntity::getUpdateFlag, entity.getUpdateFlag());
    }
}
