package com.cloudrive.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudrive.common.enums.FileStatus;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.entity.FileInfoEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfoEntity> {

    /* 以下默认实现即可满足业务，无需 XML */

    default List<FileInfoEntity> findByUserIdAndParentIdIsNullAndIsDeletedFalse(Long userId) {
        return selectList(Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getUserId, userId)
                .isNull(FileInfoEntity::getParentId)
                .eq(FileInfoEntity::getIsDeleted, false));
    }

    default List<FileInfoEntity> findByUserIdAndParentIdAndIsDeletedFalse(Long userId, Long parentId) {
        return selectList(Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getUserId, userId)
                .eq(FileInfoEntity::getParentId, parentId)
                .eq(FileInfoEntity::getIsDeleted, false));
    }


    // 判断OSS path重复，只要是未彻底删除的，都要计数，防止文件恢复时，误删了导致没有对应path
    default long countByPathAndIsDeletedFalse(String path) {
        List<Integer> status = new ArrayList<>();
        Collections.addAll(status,0,1,2);
        return selectCount(Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getObject,path)
                .in(FileInfoEntity::getIsDeleted, status));
    }

    default List<FileInfoEntity> searchByFilename(Long userId, String keyword) {
        return selectList(Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getUserId, userId)
                .like(FileInfoEntity::getFilename, keyword)
                .eq(FileInfoEntity::getIsDeleted, false));
    }

    // 分页查询方法 - 一次性获取总数和分页数据
    default Page<FileInfoEntity> findByUserIdAndParentIdIsNullAndIsDeletedFalseWithPagination(FileQueryDTO queryDTO) {
        Page<FileInfoEntity> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<FileInfoEntity> wrapper = Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getUserId, queryDTO.getUserId())
                .eq(FileInfoEntity::getParentId,0)
                .eq(FileInfoEntity::getIsDeleted, false);
        
        // 添加排序
        addOrderBy(wrapper, queryDTO.getSortBy(), queryDTO.getSortDirection());
        
        return selectPage(page, wrapper);
    }

    // 查询回收站文件列表（状态为‘1’）
    default Page<FileInfoEntity> selectRecycleListFiles(FileQueryDTO queryDTO){
        Page<FileInfoEntity> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<FileInfoEntity> wrapper = Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getUserId, queryDTO.getUserId())
                .eq(FileInfoEntity::getIsDeleted, 1);

        // 添加排序
        addOrderBy(wrapper, queryDTO.getSortBy(), queryDTO.getSortDirection());

        return selectPage(page, wrapper);
    }

    default Page<FileInfoEntity> findByUserIdAndParentIdAndIsDeletedFalseWithPagination(FileQueryDTO queryDTO) {
        Page<FileInfoEntity> page = new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize());
        LambdaQueryWrapper<FileInfoEntity> wrapper = Wrappers.<FileInfoEntity>lambdaQuery()
                .eq(FileInfoEntity::getUserId, queryDTO.getUserId())
                .eq(FileInfoEntity::getParentId, queryDTO.getParentId())
                .eq(FileInfoEntity::getIsDeleted, false);
        
        // 添加排序
        addOrderBy(wrapper, queryDTO.getSortBy(), queryDTO.getSortDirection());
        
        return selectPage(page, wrapper);
    }

    /**
     * 添加排序条件
     */
    private void addOrderBy(LambdaQueryWrapper<FileInfoEntity> wrapper, String sortBy, String sortDirection) {
        boolean isAsc = "asc".equalsIgnoreCase(sortDirection);
        
        if ("filename".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfoEntity::getFilename);
        } else if ("filesize".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfoEntity::getSize);
        } else if ("updateAt".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfoEntity::getUpdatedAt);
        } else if("deleteTime".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, FileInfoEntity::getDeleteTime);
        } else {
            // 默认按创建时间排序
            wrapper.orderBy(true, isAsc, FileInfoEntity::getCreatedAt);
        }
    }


}