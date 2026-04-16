package com.spectrometer.subsystem;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpectrumStorage {
    public static final String EXPORT_FILE_HEADER_TXT = "DATATYPE\t IR Spectrum\nXYUNITS\t Wavenumber;Energy\nDECIMALSYMBOL\t .\n";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    public static final String INST_SPC_START = "Inst. Spc Start";

    // ==========================================
    // 写入模块 (Write)
    // ==========================================

    public String saveToTxt(float[] data, Map<String, String> meta,
                            double laserFreq, double startWave, double stopWave, String dirPath) throws IOException {

        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dirPath);
        }

        String filePath = dirPath + (dirPath.endsWith("/") || dirPath.endsWith("\\") ? "" : File.separator)
                + "Sample_" + LocalDateTime.now().format(FORMATTER) + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(EXPORT_FILE_HEADER_TXT);

            int spcStart = 0;
            if (meta != null && meta.containsKey(INST_SPC_START)) {
                Matcher m = Pattern.compile("(-?\\d+)").matcher(meta.get(INST_SPC_START));
                if (m.find()) {
                    spcStart = Integer.parseInt(m.group(1));
                }
            }

            double realStepWave = laserFreq / data.length;
            int indexStart = (int) Math.floor(startWave / realStepWave) - spcStart;
            int indexStop = (int) Math.ceil(stopWave / realStepWave) - spcStart;

            indexStart = Math.max(0, indexStart);
            indexStop = Math.min(data.length - 1, indexStop);

            for (int i = indexStart; i <= indexStop; i++) {
                double currentWavenumber = (spcStart + i) * realStepWave;
                writer.write(currentWavenumber + "\t " + data[i] + "\n");
            }

            if (meta != null && !meta.isEmpty()) {
                for (Map.Entry<String, String> entry : meta.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
        }
        return filePath;
    }

    public String saveToSpc(float[] data, Map<String, String> meta,
                            double laserFreq, double startWave, double stopWave, String dirPath) throws IOException {

        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dirPath);
        }

        LocalDateTime now = LocalDateTime.now();
        String filePath = dirPath + (dirPath.endsWith("/") || dirPath.endsWith("\\") ? "" : File.separator)
                + "Sample_" + now.format(FORMATTER) + ".spc";

        // 1. 获取硬件偏移量 (保持不变)
        int spcStart = 0;
        if (meta != null && meta.containsKey(INST_SPC_START)) {
            Matcher m = Pattern.compile("(-?\\d+)").matcher(meta.get(INST_SPC_START));
            if (m.find()) spcStart = Integer.parseInt(m.group(1));
        }

        // 2. [核心优化] 劫持硬件下发的极高精度采样网格波数 (Sampling Grid Wavenumber)
        double exactGridWavenumber = laserFreq; // 默认使用配置文件的 laserFreq 兜底
        if (meta != null) {
            try {
                // 依次尝试匹配可能的硬件状态键名
                if (meta.containsKey("Sampling Grid Wavenumber")) {
                    exactGridWavenumber = Double.parseDouble(meta.get("Sampling Grid Wavenumber"));
                } else if (meta.containsKey("VCSEL Sampling Grid Wavenumber")) {
                    exactGridWavenumber = Double.parseDouble(meta.get("VCSEL Sampling Grid Wavenumber"));
                } else if (meta.containsKey("(c) Sampling Grid Wavenumber")) {
                    exactGridWavenumber = Double.parseDouble(meta.get("(c) Sampling Grid Wavenumber"));
                }
            } catch (NumberFormatException ignored) {
                // 转换失败时静默降级到传参值
            }
        }

        // 3. 计算物理真实步长 (使用 exactGridWavenumber 替代原本的 laserFreq)
        double realStepWave = exactGridWavenumber / data.length;

        // 4. 物理索引对齐算法 (保持不变)
        int indexStart = (int) Math.floor(startWave / realStepWave) - spcStart;
        int indexStop = (int) Math.ceil(stopWave / realStepWave) - spcStart;
        indexStart = Math.max(0, indexStart);
        indexStop = Math.min(data.length - 1, indexStop);

        int fnpts = indexStop - indexStart + 1;
        if (fnpts <= 0) return null;

        // 【算法精度飞跃】：计算精确首尾坐标时，必须叠加 calibAdjust 漂移补偿！
        double ffirst = ((spcStart + indexStart) * realStepWave);
        double flast = ((spcStart + indexStop) * realStepWave);

        int logOffset = 512 + 32 + (fnpts * 4);

        try (FileOutputStream fos = new FileOutputStream(filePath);
             FileChannel channel = fos.getChannel()) {

            // 1. SPC 主头部 (512 字节)
            ByteBuffer mainHeader = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            mainHeader.put(0, (byte) 0);
            mainHeader.put(1, (byte) 0x4B);
            mainHeader.put(2, (byte) 2);
            mainHeader.put(3, (byte) 0x80);
            mainHeader.putInt(4, fnpts);
            mainHeader.putDouble(8, ffirst);
            mainHeader.putDouble(16, flast);
            mainHeader.putInt(24, 1);
            mainHeader.put(28, (byte) 1);
            mainHeader.put(29, (byte) 0);

            int fdate = (now.getYear() << 20) | (now.getMonthValue() << 16) | (now.getDayOfMonth() << 11)
                    | (now.getHour() << 6) | now.getMinute();
            mainHeader.putInt(32, fdate);

            mainHeader.putInt(252, logOffset);
            channel.write(mainHeader);

            // 2. 子文件头部 (32 字节)
            ByteBuffer subHeader = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
            subHeader.put(0, (byte) 0);
            subHeader.put(1, (byte) 0x80);
            subHeader.putShort(2, (short) 0);
            subHeader.putInt(16, fnpts);
            channel.write(subHeader);

            // 3. 数据体
            ByteBuffer dataBuffer = ByteBuffer.allocate(fnpts * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = indexStart; i <= indexStop; i++) {
                dataBuffer.putFloat(data[i]);
            }
            dataBuffer.flip();
            channel.write(dataBuffer);

            // 4. 日志块
            if (meta != null && !meta.isEmpty()) {
                StringBuilder logText = new StringBuilder();
                for (Map.Entry<String, String> entry : meta.entrySet()) {
                    String val = entry.getValue();
                    if (val != null && !val.trim().isEmpty()) {
                        logText.append(entry.getKey()).append("=").append(val.trim()).append("\r\n");
                    }
                }
                byte[] logBytes = logText.toString().getBytes(StandardCharsets.US_ASCII);
                int logHeaderSize = 64;
                int rawLogSize = logHeaderSize + logBytes.length + 1;
                int padding = (4 - (rawLogSize % 4)) % 4;
                int logSize = rawLogSize + padding;

                ByteBuffer logHeader = ByteBuffer.allocate(logHeaderSize).order(ByteOrder.LITTLE_ENDIAN);
                logHeader.putInt(0, logSize);
                logHeader.putInt(4, logSize);
                logHeader.putInt(8, logHeaderSize);
                channel.write(logHeader);

                ByteBuffer logTextBuffer = ByteBuffer.allocate(logBytes.length + 1 + padding);
                logTextBuffer.put(logBytes);
                logTextBuffer.put((byte) 0);
                logTextBuffer.flip();
                channel.write(logTextBuffer);
            }
            return filePath;
        }
    }

    // ==========================================
    // 读取模块 (Read)
    // ==========================================

    /**
     * 【新增功能】：智能判定后缀并从磁盘文件解析光谱数据
     */
    public double[][] readFromFile(String filePath) throws IOException {
        if (filePath.toLowerCase().endsWith(".txt")) {
            return readFromTxt(filePath);
        } else if (filePath.toLowerCase().endsWith(".spc")) {
            return readFromSpc(filePath);
        }
        throw new IOException("不支持的文件格式解析: " + filePath);
    }

    /**
     * 【新增功能】：解析 TXT 光谱文件
     */
    private double[][] readFromTxt(String filePath) throws IOException {
        java.util.List<Double> xList = new java.util.ArrayList<>();
        java.util.List<Double> yList = new java.util.ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 遇到 '=' 说明进入了尾部的 Metadata 状态数据区，直接中断解析数据体
                if (line.contains("=")) {
                    break;
                }

                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    try {
                        double x = Double.parseDouble(parts[0]);
                        double y = Double.parseDouble(parts[1]);
                        xList.add(x);
                        yList.add(y);
                    } catch (NumberFormatException e) {
                        // 忽略头部的纯文本（如 DATATYPE, XYUNITS）
                    }
                }
            }
        }

        double[][] result = new double[2][xList.size()];
        for (int i = 0; i < xList.size(); i++) {
            result[0][i] = xList.get(i);
            result[1][i] = yList.get(i);
        }
        return result;
    }

    /**
     * 【新增功能】：解析高密度二进制 SPC 光谱文件
     */
    private double[][] readFromSpc(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             FileChannel channel = fis.getChannel()) {

            // 读取 512 字节主头部
            ByteBuffer headerBuf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(headerBuf);
            headerBuf.position(0);

            // 提取关键参数
            int fnpts = headerBuf.getInt(4);
            double ffirst = headerBuf.getDouble(8);
            double flast = headerBuf.getDouble(16);

            // 跳过 32 字节的子文件头部，游标定位到 544 字节的数据区起点
            channel.position(512 + 32);

            // 读取纯浮点数据区
            ByteBuffer dataBuf = ByteBuffer.allocate(fnpts * 4).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(dataBuf);
            dataBuf.position(0);

            double[][] result = new double[2][fnpts];
            double step = (fnpts > 1) ? (flast - ffirst) / (fnpts - 1) : 0;

            for (int i = 0; i < fnpts; i++) {
                // 根据第一点坐标和步长自动推算出 X 轴波数
                result[0][i] = ffirst + i * step;
                // 读取 Y 轴真实强度
                result[1][i] = dataBuf.getFloat();
            }
            return result;
        }
    }

    // 内存提取接口 (保留以备回退)
    public double[][] getSpectrumDataArray(float[] data, Map<String, String> meta,
                                           double laserFreq, double startWave, double stopWave) {
        int spcStart = 0;
        if (meta != null && meta.containsKey(INST_SPC_START)) {
            Matcher m = Pattern.compile("(-?\\d+)").matcher(meta.get(INST_SPC_START));
            if (m.find()) {
                spcStart = Integer.parseInt(m.group(1));
            }
        }
        double realStepWave = laserFreq / data.length;
        int indexStart = (int) Math.floor(startWave / realStepWave) - spcStart;
        int indexStop = (int) Math.ceil(stopWave / realStepWave) - spcStart;
        indexStart = Math.max(0, indexStart);
        indexStop = Math.min(data.length - 1, indexStop);
        int fnpts = indexStop - indexStart + 1;

        if (fnpts <= 0) {
            return new double[2][0];
        }

        double[] xArray = new double[fnpts];
        double[] yArray = new double[fnpts];

        for (int i = 0; i < fnpts; i++) {
            int actualIndex = indexStart + i;
            xArray[i] = (spcStart + actualIndex) * realStepWave;
            yArray[i] = data[actualIndex];
        }
        return new double[][]{xArray, yArray};
    }
}