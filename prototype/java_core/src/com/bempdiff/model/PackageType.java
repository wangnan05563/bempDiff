package com.bempdiff.model;

/** 包类型（FR1.3 / FR2.7）。对应 prototype: detect_package_type */
public enum PackageType {
    WAR,
    FAT_JAR,
    JAR,
    /** 文件夹（FR：新增的文件夹比较功能，直接对目录树做内容/结构比对）。 */
    FOLDER
}
