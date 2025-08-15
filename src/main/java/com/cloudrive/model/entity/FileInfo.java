package com.cloudrive.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("t_file_info")
public class FileInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("file_name")
    private String filename;

    @TableField("original_file_name")
    private String originalFilename;

    @TableField("path")
    private String path;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_type")
    private String fileType;

    @TableField("sha256_hash")
    private String sha256Hash;

    /** 若仍需关联用户，可保留 userId 字段，用业务代码手动 join 查询 */
    @TableField("user_id")
    private Long userId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("is_folder")
    private Boolean isFolder;

    /** 文件状态 0正常 1逻辑删除 2物理删除/*/
    @TableField("is_deleted")
    private Integer isDeleted;

    /** 自动填充创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 自动填充更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 文件删除时间 */
    @TableField("delete_time")
    private LocalDateTime deleteTime;


} 