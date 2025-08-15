package com.cloudrive.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.common.enums.FileStatus;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.entity.FileInfo;
import com.cloudrive.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    /* 以下默认实现即可满足业务，无需 XML */

    default List<FileInfo> findByUserIdAndParentIdIsNullAndIsDeletedFalse(Long userId) {
        return selectList(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .isNull(FileInfo::getParentId)
                .eq(FileInfo::getIsDeleted, false));
    }

    default List<FileInfo> findByUserIdAndParentIdAndIsDeletedFalse(Long userId, Long parentId) {
        return selectList(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getParentId, parentId)
                .eq(FileInfo::getIsDeleted, false));
    }

    default List<FileInfo> findBySha256HashAndUserIdAndIsDeletedFalse(String sha256Hash, Long userId) {
        return selectList(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getSha256Hash, sha256Hash)
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getIsDeleted, FileStatus.NORMAL.getCode()));
    }

    // 判断OSS path重复，只要是未彻底删除的，都要计数，防止文件恢复时，误删了导致没有对应path
    default long countByPathAndIsDeletedFalse(String path) {
        List<Integer> status = new ArrayList<>();
        Collections.addAll(status,0,1,2);
        return selectCount(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getPath, path)
                .in(FileInfo::getIsDeleted, status));
    }

    default List<FileInfo> searchByFilename(Long userId, String keyword) {
        return selectList(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getUserId, userId)
                .like(FileInfo::getFilename, keyword)
                .eq(FileInfo::getIsDeleted, false));
    }

    // 分页查询方法 - 一次性获取总数和分页数据
    default Page<FileInfo> findByUserIdAndParentIdIsNullAndIsDeletedFalseWithPagination(FileQueryDTO queryDTO) {
        Page<FileInfo> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<FileInfo> wrapper = Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getUserId, queryDTO.getUserId())
                .isNull(FileInfo::getParentId)
                .eq(FileInfo::getIsDeleted, false);
        
        // 添加排序
        addOrderBy(wrapper, queryDTO.getSortBy(), queryDTO.getSortDirection());
        
        return selectPage(page, wrapper);
    }

    // 查询回收站文件列表（状态为‘1’）
    default Page<FileInfo> selectRecycleListFiles(FileQueryDTO queryDTO){
        Page<FileInfo> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<FileInfo> wrapper = Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getUserId, queryDTO.getUserId())
                .eq(FileInfo::getIsDeleted, 1);

        // 添加排序
        addOrderBy(wrapper, queryDTO.getSortBy(), queryDTO.getSortDirection());

        return selectPage(page, wrapper);
    }

    default Page<FileInfo> findByUserIdAndParentIdAndIsDeletedFalseWithPagination(FileQueryDTO queryDTO) {
        Page<FileInfo> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<FileInfo> wrapper = Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getUserId, queryDTO.getUserId())
                .eq(FileInfo::getParentId, queryDTO.getParentId())
                .eq(FileInfo::getIsDeleted, false);
        
        // 添加排序
        addOrderBy(wrapper, queryDTO.getSortBy(), queryDTO.getSortDirection());
        
        return selectPage(page, wrapper);
    }

    /**
     * 添加排序条件
     */
    private void addOrderBy(LambdaQueryWrapper<FileInfo> wrapper, String sortBy, String sortDirection) {
        boolean isAsc = "asc".equalsIgnoreCase(sortDirection);
        
        if ("filename".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfo::getFilename);
        } else if ("filesize".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfo::getFileSize);
        } else if ("updateAt".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfo::getUpdatedAt);
        } else if("deleteTime".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfo::getDeleteTime);
        } else {
            // 默认按创建时间排序
            wrapper.orderBy(true, isAsc, FileInfo::getCreatedAt);
        }
    }


}