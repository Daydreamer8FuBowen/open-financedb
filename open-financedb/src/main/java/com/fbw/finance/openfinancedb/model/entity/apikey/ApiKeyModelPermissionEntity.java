package com.fbw.finance.openfinancedb.model.entity.apikey;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("api_key_model_permission")
public class ApiKeyModelPermissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long apiKeyId;
    private String provider;
    private String modelName;
    private Integer enabled;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
