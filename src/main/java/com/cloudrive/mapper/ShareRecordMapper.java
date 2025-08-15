package com.cloudrive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudrive.model.entity.ShareRecord;
import com.cloudrive.model.vo.ShareFileVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

public interface ShareRecordMapper extends BaseMapper<ShareRecord> {


    @Select("SELECT * FROM t_share_record WHERE share_code = #{shareCode}")
    @Results({
            @Result(column = "user_id", property = "userId"),
            @Result(column = "file_id", property = "fileId"),
            @Result(column = "user_id", property = "user",
                    one = @One(select = "com.cloudrive.mapper.UserMapper.selectById")),
            @Result(column = "file_id", property = "file",
                    one = @One(select = "com.cloudrive.mapper.FileInfoMapper.selectById"))
    })
    ShareRecord selectByShareCode(String shareCode);

    List<ShareRecord> selectByUserIdOrderByCreateTimeDesc(Long userId);

    @Select("SELECT * FROM t_share_record WHERE expire_time < #{now} AND is_expired = 0")
    List<ShareRecord> selectExpiredButNotMarked(LocalDateTime now);

    @Update("UPDATE t_share_record SET is_expired = 1 WHERE id = #{id}")
    int markExpired(Long id);

    @Update("UPDATE t_share_record SET visit_count = visit_count + 1 WHERE id = #{id}")
    int incrementVisitCount(Long id);

    @Delete("DELETE FROM t_share_record WHERE share_code = #{shareCode}")
    int deleteByShareCode(String shareCode);

    @Update("UPDATE t_share_record SET visit_count = #{visitCount} WHERE id = #{id}")
    int updateVisitCount(@Param("id") Long id, @Param("visitCount") Integer visitCount);

    /**
     * 联表查询分享记录及文件名等信息，返回ShareFileVO列表
     */
    @Select("SELECT s.share_code, s.expire_time, s.password, s.file_id, s.create_time, s.visit_count, s.is_expired, f.file_name, f.file_size " +
            "FROM t_share_record s LEFT JOIN t_file_info f ON s.file_id = f.id WHERE s.user_id = #{userId} ORDER BY s.create_time DESC")
    @Results({
        @Result(column = "share_code", property = "shareCode"),
        @Result(column = "expire_time", property = "expireTime"),
        @Result(column = "password", property = "password"),
        @Result(column = "file_id", property = "fileId"),
        @Result(column = "file_name", property = "filename"),
        @Result(column = "file_size", property = "fileSize"),
        @Result(column = "create_time", property = "createTime"),
        @Result(column = "visit_count", property = "visitCount"),
        @Result(column = "is_expired", property = "isExpired"),
        // hasPassword 由Service层处理
    })
    List<ShareFileVO> selectShareFileVOsByUserId(@Param("userId") Long userId);
}