package com.fbw.finance.openfinancedb.model.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 股票同步状态实体，对应 `stock_sync_state` 表。
 */
@Data
@TableName("stock_sync_state")
public class StockSyncStateEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 股票代码。 */
    private String symbol;
    /** 同步数据类型。 */
    private String dataType;
    /** 同步起始时间。 */
    private LocalDateTime startTime;
    /** 最近同步时间。 */
    private LocalDateTime latestSyncTime;
    /** 目标同步时间。 */
    private LocalDateTime targetSyncTime;
    /** 最近成功时间。 */
    private LocalDateTime lastSuccessTime;
    /** 最近失败时间。 */
    private LocalDateTime lastFailedTime;
    /** 当前同步状态。 */
    private String syncStatus;
    /** 重试次数。 */
    private Integer retryCount;
    /** 数据来源。 */
    private String dataSource;
    /** 最近一次错误信息。 */
    private String lastError;
    /** 创建时间。 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    /** 更新时间。 */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
