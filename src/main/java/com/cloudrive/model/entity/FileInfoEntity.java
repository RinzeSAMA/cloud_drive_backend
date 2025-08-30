package com.cloudrive.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@TableName("file_info")
@Accessors(chain = true)
public class FileInfoEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("file_name")
    private String filename;

    @TableField("original_file_name")
    private String originFileName;

    @TableField("path")
    private String url;

    @TableField("file_size")
    private Long size;

    @TableField("file_type")
    private String type;

    @TableField("sha256_hash")
    private String md5;

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
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    /** 自动填充更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updatedAt;

    /** 文件删除时间 */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    /** 存储桶 */
    @TableField("bucket")
    private String bucket;

    /** minio中文件名 */
    @TableField("object")
    private String object;

    /** 分片大小 */
    @TableField("chunk_size")
    private Long chunkSize;

    /** 分片数量 */
    @TableField("chunk_count")
    private Integer chunkCount;
} 