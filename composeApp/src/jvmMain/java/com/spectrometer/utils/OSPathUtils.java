package com.trionesdev.oca.core.shared.spectrometer.utils;

import java.nio.file.Paths;
import java.util.Locale;

public class OSPathUtils {
    /**
     * 定义操作系统类型枚举
     */
    public enum OSType {
        WINDOWS, LINUX, MACOS, UNKNOWN
    }

    /**
     * 获取当前操作系统类型
     */
    public static OSType getOperatingSystem() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        if (os.contains("win")) {
            return OSType.WINDOWS;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return OSType.LINUX;
        } else if (os.contains("mac")) {
            return OSType.MACOS;
        } else {
            return OSType.UNKNOWN;
        }
    }

    /**
     * 根据系统返回对应的保存路径
     * 使用 Java 17 Switch Expression 特性
     */
    public static String getSavePath(String fileName) {
        OSType osType = getOperatingSystem();

        // 使用 yield 或直接返回值
        String baseDir = switch (osType) {
            case WINDOWS -> "C:\\AppData\\uploads\\";
            case LINUX -> "/var/lib/uploads/";
            case MACOS -> "/Users/Shared/uploads/";
            case UNKNOWN -> System.getProperty("user.home") + "/downloads/";
        };

        // 使用 nio.file.Paths 自动处理路径分隔符
        return Paths.get(baseDir, fileName).toString();
    }
}
