package com.cloudrive.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cloudrive.model.entity.ShareRecordEntity;
import com.cloudrive.model.vo.ShareFileVO;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件分享服务接口
 */
public interface ShareService extends IService<ShareRecordEntity> {
    /**
     * 创建分享链接
     */
    ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password);

    /**
     * 访问分享文件
     */
    ShareFileVO accessShare(String shareCode, String password);

    /**
     * 通过令牌访问分享文件
     */
    ShareFileVO accessShareByToken(String shareCode, String token);

    /**
     * 生成分享访问令牌
     */
    String generateShareToken(String shareCode, String password);

    /**
     * 验证分享令牌
     */
    boolean validateShareToken(String shareCode, String token);

    /**
     * 检查分享是否过期
     */
    boolean isShareExpired(String shareCode);

    /**
     * 标记过期的分享记录
     */
    void markExpiredShares();

    /**
     * 获取当前用户的所有分享文件列表
     */
    List<ShareFileVO> getSharedFiles();

    /**
     * 取消分享
     * @param shareCode 分享码
     */
    void cancelShare(String shareCode);

    /**
     * 下载分享文件
     * @param shareCode 分享码
     * @param token 访问令牌
     * @return 文件直链
     */
    String downloadSharedFile(String shareCode, String token);

    /**
     * 获取分享文件的文件名
     * @param shareCode 分享码
     * @return 文件名
     */
    String getFilename(String shareCode);
} 