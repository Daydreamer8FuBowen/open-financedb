package com.fbw.finance.openfinancedb.model.entity.apikey;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("api_key")
public class ApiKeyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyName;
    private String apiKey;
    @TableField("is_admin")
    private Boolean isAdmin;
    private Integer status;
    private LocalDateTime expiresAt;
    private Integer qpsLimit;
    private Long dailyQuota;
    private LocalDateTime lastUsedAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
