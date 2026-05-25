package com.fbw.finance.openfinancedb.repository.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fbw.finance.openfinancedb.controller.data.vo.req.TradeCalendarPageReqVO;
import com.fbw.finance.openfinancedb.framework.web.PageResult;
import com.fbw.finance.openfinancedb.model.entity.data.TradeCalendarEntity;
import com.fbw.finance.openfinancedb.repository.data.TradeCalendarRepository;
import com.fbw.finance.openfinancedb.repository.data.mapper.TradeCalendarMapper;
import com.fbw.finance.openfinancedb.repository.support.RepositoryQueryHelper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TradeCalendarRepositoryImpl implements TradeCalendarRepository {

    private final TradeCalendarMapper tradeCalendarMapper;

    public TradeCalendarRepositoryImpl(TradeCalendarMapper tradeCalendarMapper) {
        this.tradeCalendarMapper = tradeCalendarMapper;
    }

    @Override
    public Long create(TradeCalendarEntity entity) {
        return tradeCalendarMapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override
    public boolean update(TradeCalendarEntity entity) {
        LambdaUpdateWrapper<TradeCalendarEntity> updateWrapper = new LambdaUpdateWrapper<TradeCalendarEntity>()
                .eq(TradeCalendarEntity::getId, entity.getId())
                .set(TradeCalendarEntity::getExchange, entity.getExchange())
                .set(TradeCalendarEntity::getTradeDate, entity.getTradeDate())
                .set(TradeCalendarEntity::getIsOpen, entity.getIsOpen())
                .set(TradeCalendarEntity::getPreTradeDate, entity.getPreTradeDate())
                .set(TradeCalendarEntity::getNextTradeDate, entity.getNextTradeDate());
        return tradeCalendarMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public boolean upsertByExchangeAndTradeDate(TradeCalendarEntity entity) {
        Optional<TradeCalendarEntity> existing = findByExchangeAndTradeDate(entity.getExchange(), entity.getTradeDate());
        if (existing.isEmpty()) {
            return create(entity) != null;
        }
        entity.setId(existing.get().getId());
        return update(entity);
    }

    @Override
    public boolean deleteById(Long id) {
        return tradeCalendarMapper.deleteById(id) > 0;
    }

    @Override
    public Optional<TradeCalendarEntity> findById(Long id) {
        return Optional.ofNullable(tradeCalendarMapper.selectById(id));
    }

    @Override
    public Optional<TradeCalendarEntity> findByExchangeAndTradeDate(String exchange, LocalDate tradeDate) {
        LambdaQueryWrapper<TradeCalendarEntity> queryWrapper = new LambdaQueryWrapper<TradeCalendarEntity>()
                .eq(TradeCalendarEntity::getExchange, exchange)
                .eq(TradeCalendarEntity::getTradeDate, tradeDate);
        return Optional.ofNullable(tradeCalendarMapper.selectOne(queryWrapper));
    }

    @Override
    public long count() {
        return tradeCalendarMapper.selectCount(null);
    }

    @Override
    public List<TradeCalendarEntity> findOpenDays(String exchange, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<TradeCalendarEntity> queryWrapper = new LambdaQueryWrapper<TradeCalendarEntity>()
                .eq(TradeCalendarEntity::getExchange, exchange)
                .eq(TradeCalendarEntity::getIsOpen, true)
                .ge(TradeCalendarEntity::getTradeDate, startDate)
                .le(TradeCalendarEntity::getTradeDate, endDate)
                .orderByAsc(TradeCalendarEntity::getTradeDate);
        return tradeCalendarMapper.selectList(queryWrapper);
    }

    @Override
    public PageResult<TradeCalendarEntity> page(TradeCalendarPageReqVO reqVO) {
        LambdaQueryWrapper<TradeCalendarEntity> queryWrapper = buildQueryWrapper(reqVO)
                .orderByDesc(TradeCalendarEntity::getTradeDate)
                .orderByDesc(TradeCalendarEntity::getId);
        return RepositoryQueryHelper.selectPage(tradeCalendarMapper, reqVO.getPageNo(), reqVO.getPageSize(), queryWrapper);
    }

    private LambdaQueryWrapper<TradeCalendarEntity> buildQueryWrapper(TradeCalendarPageReqVO reqVO) {
        LambdaQueryWrapper<TradeCalendarEntity> queryWrapper = RepositoryQueryHelper.lambdaQuery();
        RepositoryQueryHelper.eqIfHasText(queryWrapper, TradeCalendarEntity::getExchange, reqVO.getExchange());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, TradeCalendarEntity::getTradeDate, reqVO.getTradeDate());
        RepositoryQueryHelper.eqIfPresent(queryWrapper, TradeCalendarEntity::getIsOpen, reqVO.getIsOpen());
        return queryWrapper;
    }
}
