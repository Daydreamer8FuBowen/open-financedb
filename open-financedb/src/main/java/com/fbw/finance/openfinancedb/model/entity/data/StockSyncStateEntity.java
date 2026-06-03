package com.fbw.finance.openfinancedb.model.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Sync state row for the stock_sync_state table.
 */
@Data
@TableName("stock_sync_state")
public class StockSyncStateEntity {

    /** Primary key. */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** Stock symbol. */
    private String symbol;
    /** Sync data type. */
    private String dataType;
    /** Data start time for this sync stream. */
    private LocalDateTime startTime;
    /** Latest successfully processed data time. */
    private LocalDateTime latestSyncTime;
    /** Next cursor time to continue processing. */
    private LocalDateTime cursorTime;
    /** Last successful attempt time. */
    private LocalDateTime lastSuccessTime;
    /** Last failed attempt time. */
    private LocalDateTime lastFailedTime;
    /** Current sync status. */
    private String syncStatus;
    /** Retry count. */
    private Integer retryCount;
    /** Data source. */
    private String dataSource;
    /** Last error message. */
    private String lastError;
    /** Created time. */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    /** Updated time. */
    @TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
