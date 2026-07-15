package com.cloudstorage.repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloudstorage.model.entity.ShareLink;
import com.cloudstorage.model.entity.User;
import com.cloudstorage.model.entity.UserFile;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByVerificationCode(String code); // 由校验码查找被分享的文件

    List<ShareLink> findByOwner(User owner); // 查找当前用户的分享文件 - 构造 “我的分享” 列表

    boolean existsByVerificationCode(String code); // 检查当前校验码的唯一性，防止被撞库

    void deleteByIdAndOwner(Long id, User owner); // 删除分享文件 - 根据 文件id 和 分享者查找

    boolean existsByShareFileAndOwner(UserFile file, User owner); // 检查分享文件和所有者是否同时存在于数据库中 - 避免同一文件被多次分享

    List<ShareLink> findByExpireTimeBefore(LocalDateTime time); // 查找所有过期的分享文件 - 统一销毁

    Optional<ShareLink> findByShareFileAndOwner(UserFile shareFile, User owner); // 根据 文件ID 和 所有者实体 查找 校验码
}