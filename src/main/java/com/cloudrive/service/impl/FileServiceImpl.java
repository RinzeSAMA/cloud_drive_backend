package com.cloudrive.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.common.constant.CommonConstants;
import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.enums.FileStatus;
import com.cloudrive.common.exception.BusinessException;
import com.cloudrive.common.mq.constants.MQConstants;
import com.cloudrive.common.util.*;
import com.cloudrive.convert.FileConvertUtil;
import com.cloudrive.mapper.FileInfoMapper;
import com.cloudrive.mapper.UserMapper;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.entity.FileInfo;
import com.cloudrive.model.entity.User;
import com.cloudrive.model.vo.FileListVO;
import com.cloudrive.model.vo.FileListPageVO;
import com.cloudrive.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 文件服务实现类
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    private final StorageServiceFactory storageServiceFactory;

    private final FileInfoMapper fileInfoMapper;


    private final UploadProgressService uploadProgressService;

    private final UserMapper userMapper;

    private final RabbitTemplate rabbitTemplate;

    private final ThreadPoolTaskExecutor mqSendExecutor;

    @Override
    @Transactional
    public String uploadFile(MultipartFile file, Long parentId) {
        User currentUser = UserContext.getCurrentUser();
        String sha256Hash = FileHashUtil.calculateSHA256(file);

        if (sha256Hash != null && !sha256Hash.isEmpty()) {
         // 查找当前用户是否已经上传过相同哈希值的文件
            List<FileInfo> existingFiles = fileInfoMapper.findBySha256HashAndUserIdAndIsDeletedFalse(sha256Hash, currentUser.getId());
           if (!existingFiles.isEmpty()) {
                // 找到了相同哈希值的文件，实现秒传
                FileInfo existingFile = existingFiles.get(0);
                // 使用通用的秒传处理方法，传入null表示不需要进度跟踪
                FileInfo newFileInfo = handleFastUpload(file.getOriginalFilename(), file.getSize(), existingFile, sha256Hash, parentId, null, currentUser);
                return newFileInfo.getPath();
            }
        }

        // 3. 如果没有找到相同哈希值的文件，执行正常上传流程
        StorageService storageService = storageServiceFactory.getStorageService();
        String path = getUploadPath(parentId, currentUser);
        String filePath = storageService.uploadFile(file, path);
        FileInfo fileInfo = FileConvertUtil.toFileInfo(file, filePath, currentUser, parentId);
        fileInfo.setSha256Hash(sha256Hash);
        fileInfoMapper.insert(fileInfo);
        return fileInfo.getPath();
    }

    @Override
    @Transactional
    public void uploadFileWithProgressFromPath(String filePath, String originalFilename, long fileSize, Long parentId, String taskId, Long userId) {
        // 使用传入的userId获取用户信息
        User currentUser = userMapper.selectById(userId);
        ExceptionUtil.throwIf(currentUser == null, ErrorCode.USER_NOT_FOUND);

        try {
            // 从文件路径创建File对象
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                logger.error("File not found or not a regular file: {}", filePath);
                uploadProgressService.completeUploadTask(taskId, false, "文件不存在或不是常规文件");
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }

            // 计算文件的SHA-256哈希值
            String sha256Hash = FileHashUtil.calculateSHA256(file);

            // 检查是否可以使用秒传逻辑
            if (sha256Hash != null && !sha256Hash.isEmpty()) {
                List<FileInfo> existingFiles = fileInfoMapper.findBySha256HashAndUserIdAndIsDeletedFalse(sha256Hash, currentUser.getId());
                if (!existingFiles.isEmpty()) {
                    // 处理秒传逻辑
                    FileInfo existingFile = existingFiles.get(0);
                    // 使用通用的秒传处理方法，传入taskId进行进度跟踪
                    handleFastUpload(originalFilename, fileSize, existingFile, sha256Hash, parentId, taskId, currentUser);
                    return;
                }
            }

            // 如果没有找到相同哈希值的文件，执行正常上传流程
//            String uploadPath = getUploadPath(parentId, currentUser);

            // 使用带进度跟踪的上传方法
            StorageService storageService = storageServiceFactory.getStorageService();
            String uploadedPath = storageService.uploadFileWithProgressFromPath(file, currentUser.getId(), taskId, originalFilename, fileSize);

            // 使用MapStruct创建文件信息记录
            // 注意：这里的originalFilename是相对路径，uploadedPath是OSS路径
            String filenameOnly = new File(originalFilename).getName();
            FileInfo fileInfo = FileConvertUtil.toFileInfoFromPath(
                filenameOnly, // 只文件名
                originalFilename, // 前端相对路径
                uploadedPath,
                fileSize,
                currentUser,
                parentId, // 直接用传入的parentId
                sha256Hash
            );
            fileInfoMapper.insert(fileInfo);
            uploadProgressService.completeUploadTask(taskId, true, "上传成功");
        } catch (Exception e) {
            // 标记任务失败
            logger.error("Error uploading file from path: {}, error: {}", filePath, e.getMessage());
            uploadProgressService.completeUploadTask(taskId, false, e.getMessage());
            throw e;
        }
    }

    /**
     * 处理秒传逻辑，可用于普通上传和带进度上传
     * 
     * @param filename 文件名（相对路径）
     * @param fileSize 文件大小
     * @param existingFile 已存在的文件
     * @param sha256Hash 文件哈希值
     * @param parentId 父目录ID
     * @param taskId 上传任务ID，如果为null则不进行进度跟踪
     * @param currentUser 当前用户
     * @return 新创建的文件信息对象
     */
    private FileInfo handleFastUpload(String filename, long fileSize, FileInfo existingFile, String sha256Hash, Long parentId, String taskId, User currentUser) {
        // 使用MapStruct创建一个新的文件记录
        FileInfo newFileInfo = FileConvertUtil.toFileInfoForFastUpload(filename, existingFile, currentUser, parentId, sha256Hash);

        // 如果有任务ID，则进行进度跟踪
        if (taskId != null) {
            // 模拟上传进度（秒传情况下直接完成）
            uploadProgressService.updateProgress(taskId, fileSize, fileSize);
            uploadProgressService.completeUploadTask(taskId, true, "文件秒传成功");
        }

        // 保存新的文件记录
        fileInfoMapper.insert(newFileInfo);
        return newFileInfo;
    }

    /**
     * 获取上传路径
     */
    private String getUploadPath(Long parentId, User currentUser) {
        String path = CommonConstants.File.FILE_PATH_PREFIX + currentUser.getId();
        if (parentId != null) {
            FileInfo parent = fileInfoMapper.selectById(parentId);
            ExceptionUtil.throwIf(parent == null, ErrorCode.FILE_NOT_FOUND);
            path = parent.getPath();
        }
        return path;
    }

    @Override
    public byte[] downloadFile(Long fileId) {
        User currentUser = UserContext.getCurrentUser();
        FileInfo fileInfo = getAndValidateFile(fileId, currentUser);
        return retrieveFileContent(fileInfo);
    }

    /**
     * 文件列表查询
     */
    @Override
    public FileListPageVO getFileListWithPagination(FileQueryDTO queryDTO) {
        // 一次性查询分页数据和总数
        Page<FileInfo> pageResult = queryDTO.getParentId() == null
                ? fileInfoMapper.findByUserIdAndParentIdIsNullAndIsDeletedFalseWithPagination(queryDTO)
                : fileInfoMapper.findByUserIdAndParentIdAndIsDeletedFalseWithPagination(queryDTO);
        // 转换为VO
        List<FileListVO> fileListVOs = pageResult.getRecords().stream()
                .map(FileConvertUtil::toFileListVO)
                .collect(Collectors.toList());
        return new FileListPageVO(fileListVOs, pageResult.getTotal(), (int) pageResult.getPages(), queryDTO.getPageNo(), queryDTO.getPageSize());
    }

    /**
     * 回收站列表查询
     */
    @Override
    public FileListPageVO getRecycleListFiles(FileQueryDTO queryDTO) {
        Page<FileInfo> pageResult = fileInfoMapper.selectRecycleListFiles(queryDTO);
        List<FileListVO> fileListVOs = pageResult.getRecords().stream()
                .map(FileConvertUtil::toFileListVO)
                .collect(Collectors.toList());
        return new FileListPageVO(fileListVOs, pageResult.getTotal(), (int) pageResult.getPages(), queryDTO.getPageNo(), queryDTO.getPageSize());
    }


    /**
     * 逻辑删除（到回收站）
     */
    @Override
    @Transactional
    public void deleteFileToRecycleBin(List<Long> fileIds) {
        User currentUser = UserContext.getCurrentUser();
        // 1. 批量查询 fileIds 对应的文件
        List<FileInfo> fileList = fileInfoMapper.selectList(
                Wrappers.<FileInfo>lambdaQuery()
                        .in(FileInfo::getId, fileIds)
                        .eq(FileInfo::getUserId, currentUser.getId())
                        .eq(FileInfo::getIsDeleted, FileStatus.NORMAL.getCode())
        );
        // 如果根据传入的数组，查出来为空；或者查出来的数据不同，则说明少查了，上抛异常
        ExceptionUtil.throwIf(fileList.isEmpty() || fileList.size()!=fileIds.size() , ErrorCode.FILE_NOT_FOUND);
        List<Long> delFileIdList = new ArrayList<>();// 先保存所有要逻辑删除的文件id

        // 2.  递归查找当前文件的子文件及子文件夹,加入delFileList

        // 加入要查找的文件状态枚举集合（此处为正常）
        List<Integer> status = new ArrayList<>();
        status.add(FileStatus.NORMAL.getCode());
        for (FileInfo fileInfo : fileList) {
            findAllSubFolderFileList(delFileIdList,currentUser.getId(),fileInfo.getId(),status);
        }

        // 3.  批量更新delFileList，状态为2（物理待删除）
        fileInfoMapper.update(
                new FileInfo(){{
                    setIsDeleted(2);
                    setDeleteTime(LocalDateTime.now());
                    setUpdatedAt(LocalDateTime.now());
                }},
                Wrappers.<FileInfo>lambdaQuery()
                        .in(FileInfo::getId, delFileIdList)
        );

       //4.   批量更新原始fileIds，状态为1（逻辑删除）
        fileInfoMapper.update(
                new FileInfo(){{
                    setIsDeleted(1);
                    setDeleteTime(LocalDateTime.now());
                    setUpdatedAt(LocalDateTime.now());
                }},
                Wrappers.<FileInfo>lambdaQuery()
                        .in(FileInfo::getId, fileIds)
        );
    }

    /**
     * 物理删除（删除OSS对应存储）
     */
    @Override
    @Transactional
    public void deleteFileForPermanent(List<Long> fileIds) {
        User currentUser = UserContext.getCurrentUser();
        // 1. 批量查询 fileIds 对应的文件
        List<FileInfo> fileList = fileInfoMapper.selectList(
                Wrappers.<FileInfo>lambdaQuery()
                        .in(FileInfo::getId, fileIds)
                        .eq(FileInfo::getUserId, currentUser.getId())
                        .eq(FileInfo::getIsDeleted, FileStatus.RECYCLED.getCode())
        );
        // 如果根据传入的数组，查出来为空；或者查出来的数据不同，则说明少查了，上抛异常
        ExceptionUtil.throwIf(fileList.isEmpty() || fileList.size()!=fileIds.size() , ErrorCode.FILE_NOT_FOUND);
        // 保存所有要物理删除的文件id
        List<Long> delFileIdList = new ArrayList<>();

        // 2.  递归查找当前文件的子文件及子文件夹,加入delFileList
        // 加入要查找的文件状态枚举集合
        List<Integer> status = new ArrayList<>();
        status.add(FileStatus.DELETE.getCode());// 待删除的数据（状态为2）
        for (FileInfo fileInfo : fileList) {
            findAllSubFolderFileList(delFileIdList,currentUser.getId(),fileInfo.getId(), status);
        }

        // 3.  筛选非文件夹的文件，得到path值集合，供后续OSS删除
        List<String> pathList = fileInfoMapper.selectList(
                Wrappers.<FileInfo>lambdaQuery()
                        .eq(FileInfo::getIsFolder, false)
                        .in(FileInfo::getId, delFileIdList)
        ).stream().map(FileInfo::getPath).toList();

        // 4.  批量更新delFileList，状态为3（已删除）
        fileInfoMapper.update(
                new FileInfo(){{
                    setIsDeleted(3);
                    setDeleteTime(LocalDateTime.now());
                    setUpdatedAt(LocalDateTime.now());
                }},
                Wrappers.<FileInfo>lambdaQuery()
                        .in(FileInfo::getId, delFileIdList)
        );
        // 5. 使用线程池，发送物理删除的消息到消息队列,异步处理物理删除操作，提高用户体验
        for (String path : pathList) {
            mqSendExecutor.execute(() -> {
                rabbitTemplate.convertAndSend(
                        MQConstants.OSS_DELETE_EXCHANGE_NAME,
                        MQConstants.OSS_DELETE_KEY,
                        path
                );
            });
        }

    }

    @Override
    @Transactional
    public void renameFile(Long fileId, String newFilename) {
        User currentUser = UserContext.getCurrentUser();
        FileInfo fileInfo = getAndValidateFile(fileId, currentUser);

        fileInfo.setFilename(newFilename);
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfoMapper.updateById(fileInfo);
    }

    @Override
    public List<FileListVO> searchFiles(String keyword) {
        Long userId = UserContext.getCurrentUserId();
        List<FileInfo> fileInfos = fileInfoMapper.searchByFilename(userId, keyword);
        return fileInfos.stream().map(FileConvertUtil::toFileListVO).collect(Collectors.toList());
    }

    @Override
    public String getFilename(Long fileId) {
        return getAndValidateFile(fileId, UserContext.getCurrentUser()).getFilename();
    }

    @Override
    public byte[] getFileContent(Long fileId) {
        FileInfo fileInfo = getAndValidateFile(fileId, UserContext.getCurrentUser());
        return retrieveFileContent(fileInfo);
    }



    @Override
    @Transactional
    public Long createFolderWithId(String folderName, Long parentId, Long userId) {
        List<FileInfo> siblings = parentId == null
            ? fileInfoMapper.findByUserIdAndParentIdIsNullAndIsDeletedFalse(userId)
            : fileInfoMapper.findByUserIdAndParentIdAndIsDeletedFalse(userId, parentId);
        boolean exists = siblings.stream()
            .anyMatch(f -> Boolean.TRUE.equals(f.getIsFolder()) && f.getFilename().equals(folderName));
        ExceptionUtil.throwIf(exists, ErrorCode.FILE_ALREADY_EXISTS);
        FileInfo folder = new FileInfo();
        folder.setFilename(folderName);
        folder.setOriginalFilename(folderName);
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setIsFolder(true);
        folder.setIsDeleted(0);
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedAt(LocalDateTime.now());
        folder.setFileSize(0L);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String path = CommonConstants.File.FILE_PATH_PREFIX + userId + "/" + uuid + "/";
        folder.setPath(path);
        fileInfoMapper.insert(folder);
        return folder.getId();
    }



    /* ---------------- 私有工具 ---------------- */
    private FileInfo getAndValidateFile(Long fileId, User currentUser) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        ExceptionUtil.throwIf(fileInfo == null, ErrorCode.FILE_NOT_FOUND);
        ExceptionUtil.throwIf(!fileInfo.getUserId().equals(currentUser.getId()), ErrorCode.NO_PERMISSION);
        ExceptionUtil.throwIf(fileInfo.getIsDeleted() != 0, ErrorCode.FILE_NOT_FOUND);

        return fileInfo;
    }

    private void validateFolderIsEmpty(Long folderId) {
        long childCount = fileInfoMapper.selectCount(
                Wrappers.<FileInfo>lambdaQuery()
                        .eq(FileInfo::getParentId, folderId)
                        .eq(FileInfo::getIsDeleted, false)
        );
        ExceptionUtil.throwIf(childCount > 0, ErrorCode.FOLDER_NOT_EMPTY);
    }

    private byte[] retrieveFileContent(FileInfo fileInfo) {
        ExceptionUtil.throwIf(fileInfo.getIsFolder(), ErrorCode.CANNOT_DOWNLOAD_FOLDER);
        return storageServiceFactory.getStorageService().downloadFile(fileInfo.getPath());
    }

    /**递归查找指定文件夹下所有子文件夹 */
    private void findAllSubFolderFileList(List<Long> fileIdList,Long userId,Long parentId,List<Integer> status){
        // 每次递归走到这时，会加入当前层的文件id,不管是文件还是文件夹
        fileIdList.add(parentId);
        // 如果是文件夹，递归查询文件夹结构，（包含子文件和子文件夹）
        List<FileInfo> fileInfos = fileInfoMapper.selectList(
                Wrappers.<FileInfo>lambdaQuery()
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getParentId, parentId)
                        .in(FileInfo::getIsDeleted,status)
        );
        for(FileInfo fileInfo : fileInfos){
            findAllSubFolderFileList(fileIdList,userId, fileInfo.getId(),status);
        }
    }
}
