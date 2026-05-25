package com.fbw.finance.openfinancedb.model.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Stock master data entity mapped to stock_info.
 */
@Data
@TableName("stock_info")
public class StockInfoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String symbol;
    private String rawSymbol;
    private String name;
    private String exchange;
    private String market;
    private String area;
    private String industry;
    private String type;
    private LocalDate listDate;
    private LocalDate delistDate;
    private String status;
    @TableField("is_realtime_sync_enabled")
    private Boolean isRealtimeSyncEnabled;
    private String actEntType;
    private String dataSource;
    private LocalDate latestQuoteDate;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
