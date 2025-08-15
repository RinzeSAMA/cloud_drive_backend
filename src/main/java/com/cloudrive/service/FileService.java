package com.cloudrive.service;

import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.vo.FileListVO;
import com.cloudrive.model.vo.FileListPageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务接口
 */
public interface FileService {
    /**
     * 上传文件
     */
    String uploadFile(MultipartFile file, Long parentId);

    /**
     * 从文件路径上传文件并跟踪进度（用于异步上传）
     *
     * @param filePath         文件路径
     * @param originalFilename 原始文件名
     * @param fileSize         文件大小
     * @param parentId         父文件夹ID
     * @param taskId           任务ID，用于跟踪进度
     * @param userId           用户ID，用于在异步线程中获取用户信息
     */
    void uploadFileWithProgressFromPath(String filePath, String originalFilename, long fileSize, Long parentId, String taskId, Long userId);

    /**
     * 下载文件
     */
    byte[] downloadFile(Long fileId);

    /**
     * 分页查询文件列表
     *
     */
    FileListPageVO getFileListWithPagination(FileQueryDTO fileQueryDTO);

    /**
     * 逻辑删除文件,支持批量
     */
    void deleteFileToRecycleBin(List<Long> fileIds);

    /**
     * 彻底删除文件（逻辑删除->物理删除）
     */
    void deleteFileForPermanent(List<Long> fileId);
    /**
     * 重命名文件
     */
    void renameFile(Long fileId, String newFilename);

    /**
     * 搜索文件
     */
    List<FileListVO> searchFiles(String keyword);

    /**
     * 获取文件名
     */
    String getFilename(Long fileId);

    /**
     * 获取文件内容
     * @param fileId 文件ID
     * @return 文件内容字节数组
     */
    byte[] getFileContent(Long fileId);

    /**
     * 新建文件夹并返回ID
     */
    Long createFolderWithId(String folderName, Long parentId, Long userId);

    FileListPageVO getRecycleListFiles(FileQueryDTO queryDTO);


}