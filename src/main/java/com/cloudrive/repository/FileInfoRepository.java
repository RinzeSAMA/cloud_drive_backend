//package com.cloudrive.repository;
//
//import com.cloudrive.model.entity.FileInfo;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {
//    List<FileInfo> findByUserIdAndParentIdIsNullAndIsDeletedFalse(Long userId);
//    List<FileInfo> findByUserIdAndParentIdAndIsDeletedFalse(Long userId, Long parentId);
//    Optional<FileInfo> findByPathAndUserId(String path, Long userId);
//    long countByParentIdAndIsDeletedFalse(Long parentId);
//
//    /**
//     * 根据SHA-256哈希值和用户ID查找未删除的文件
//     */
//    List<FileInfo> findBySha256HashAndUserIdAndIsDeletedFalse(String sha256Hash, Long userId);
//
//    /**
//     * 统计引用同一文件路径的文件数量
//     */
//    @Query("SELECT COUNT(f) FROM FileInfo f WHERE f.path = :path AND f.isDeleted = false")
//    long countByPathAndIsDeletedFalse(@Param("path") String path);
//
//    /**
//     * 根据文件名模糊搜索文件（不区分大小写）
//     */
//    @Query("SELECT f FROM FileInfo f WHERE f.user.id = :userId AND LOWER(f.filename) LIKE LOWER(CONCAT('%', :keyword, '%')) AND f.isDeleted = false")
//    List<FileInfo> searchByFilename(@Param("userId") Long userId, @Param("keyword") String keyword);
//}