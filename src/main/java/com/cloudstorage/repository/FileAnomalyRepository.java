package com.cloudstorage.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloudstorage.model.entity.FileAnomaly;

public interface FileAnomalyRepository extends JpaRepository<FileAnomaly, Long> {
    List<FileAnomaly> findByResolvedFalse(); // 搜索所有未解决的异常报告
}
