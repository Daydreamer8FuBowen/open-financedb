package com.fbw.finance.openfinancedb.service.financial.convert;

import com.fbw.finance.openfinancedb.model.entity.financial.StockIncomeStatementEntity;
import com.fbw.finance.openfinancedb.model.financial.IncomeStatementPoint;

public final class StockIncomeStatementConvert {

    private StockIncomeStatementConvert() {
    }

    public static StockIncomeStatementEntity toEntity(IncomeStatementPoint point) {
        StockIncomeStatementEntity entity = new StockIncomeStatementEntity();
        entity.setSymbol(point.symbol());
        entity.setAnnDate(point.annDate());
        entity.setFAnnDate(point.fAnnDate());
        entity.setEndDate(point.endDate());
        entity.setReportType(point.reportType());
        entity.setCompType(point.compType());
        entity.setEndType(point.endType());
        entity.setBasicEps(point.basicEps());
        entity.setDilutedEps(point.dilutedEps());
        entity.setTotalRevenue(point.totalRevenue());
        entity.setRevenue(point.revenue());
        entity.setIntIncome(point.intIncome());
        entity.setCommIncome(point.commIncome());
        entity.setNCommisIncome(point.nCommisIncome());
        entity.setNOthIncome(point.nOthIncome());
        entity.setNOthBIncome(point.nOthBIncome());
        entity.setOthBIncome(point.othBIncome());
        entity.setFvValueChgGain(point.fvValueChgGain());
        entity.setInvestIncome(point.investIncome());
        entity.setForexGain(point.forexGain());
        entity.setTotalCogs(point.totalCogs());
        entity.setIntExp(point.intExp());
        entity.setCommExp(point.commExp());
        entity.setBizTaxSurchg(point.bizTaxSurchg());
        entity.setAdminExp(point.adminExp());
        entity.setOperExp(point.operExp());
        entity.setOperateProfit(point.operateProfit());
        entity.setNonOperIncome(point.nonOperIncome());
        entity.setNonOperExp(point.nonOperExp());
        entity.setTotalProfit(point.totalProfit());
        entity.setIncomeTax(point.incomeTax());
        entity.setNetIncome(point.netIncome());
        entity.setNIncomeAttrP(point.nIncomeAttrP());
        entity.setOthComprIncome(point.othComprIncome());
        entity.setTComprIncome(point.tComprIncome());
        entity.setComprIncAttrP(point.comprIncAttrP());
        entity.setContinuedNetProfit(point.continuedNetProfit());
        entity.setUpdateFlag(point.updateFlag());
        return entity;
    }
}
