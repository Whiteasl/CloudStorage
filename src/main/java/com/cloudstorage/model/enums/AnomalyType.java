package com.cloudstorage.model.enums;

public enum AnomalyType {
    MISSING("未找到数据库中存在的文件: "), // DB -> DISK ：数据库有数据，但磁盘没有实体文件
    TYPE_MISMATCH("无法判断该文件是否为文件夹: "), // DB -> DISK : 记录上是文件，但实际是文件夹（反之一样）
    SIZE_MISMATCH("文件大小记录错误: "), // DB -> DISK : 文件大小差异超过 1KB 容差
    ORPHAN("孤儿文件: "), // DISK -> DB : 磁盘有实体文件，但数据库没有记录（孤儿文件）
    RING("文件的路径逻辑成环，无法查询到该文件的路径: "), // TREE : parentFolderId 链成环时，路径推理会死循环
    PARENT_MISSING("文件的父目录不存在: "), // TREE : parentFolderId 指向不存在的行
    PARENT_NOT_FOLDER("文件路径节点错误：父节点不是文件夹: "), // TREE : 父节点不是文件夹
    DUP_LOGICAL("同用户下两行推导出同一个逻辑路径: "), // TREE : 同用户下两行推导出同一个逻辑路径
    CLEANUP_FAILED("清理失败的残留文件: "), // CLEANUP : 残留文件
    USERDIR_NOT_EXIST("用户根文件夹不存在: "); // USERDIR_NOT_EXIST : 用户的根文件夹在磁盘中不存在

    private final String description;

    AnomalyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
