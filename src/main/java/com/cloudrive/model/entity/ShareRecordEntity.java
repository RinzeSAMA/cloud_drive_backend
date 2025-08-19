package com.cloudrive.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_share_record")
public class ShareRecordEntity {

    /** 主键自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文件 id（代替 @ManyToOne FileInfo） */
    @TableField("file_id")
    private Long fileId;

    /** 用户 id（代替 @ManyToOne User） */
    @TableField("user_id")
    private Long userId;

    /** 分享码，唯一 */
    @TableField("share_code")
    private String shareCode;

    /** 提取密码 */
    @TableField("password")
    private String password;

    /** 过期时间 */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 创建时间（业务字段） */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 是否已过期 */
    @TableField("is_expired")
    private Boolean isExpired = false;

    /** 访问次数 */
    @TableField("visit_count")
    private Integer visitCount = 0;

    /** 创建时间（自动填充） */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 非数据库字段，需要时通过联表/二次查询填充） */
    // 仅做转换用，不存在于数据库中
    @TableField(exist = false)
    private UserEntity user;
    // 仅做转换用，不存在于数据库中
    @TableField(exist = false)
    private FileInfoEntity file;
}