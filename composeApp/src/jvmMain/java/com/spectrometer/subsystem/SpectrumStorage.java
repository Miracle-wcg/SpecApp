package com.trionesdev.oca.core.shared.spectrometer.subsystem;

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

            // 1. 获取硬件偏移量
            int spcStart = 0;
            if (meta != null && meta.containsKey(INST_SPC_START)) {
                Matcher m = Pattern.compile("(-?\\d+)").matcher(meta.get(INST_SPC_START));
                if (m.find()) {
                    spcStart = Integer.parseInt(m.group(1));
                }
            }

            // 2. 计算物理真实步长
            double realStepWave = laserFreq / data.length;

            // 3. 物理索引对齐算法 (包含边界包围点)
            int indexStart = (int) Math.floor(startWave / realStepWave) - spcStart;
            int indexStop = (int) Math.ceil(stopWave / realStepWave) - spcStart;

            indexStart = Math.max(0, indexStart);
            indexStop = Math.min(data.length - 1, indexStop);

            // 4. 按确定的绝对索引直接输出
            for (int i = indexStart; i <= indexStop; i++) {
                double currentWavenumber = (spcStart + i) * realStepWave;
//                writer.write(String.format(Locale.US, "%.4f\t %.4f\n", currentWavenumber, data[i]));
                writer.write(currentWavenumber + "\t " + data[i] + "\n");
            }

            // 5. 追加状态信息
            if (meta != null && !meta.isEmpty()) {
                for (Map.Entry<String, String> entry : meta.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
        }
        return filePath;
    }

    /**
     * 导出为专业 SPC 二进制格式 (Thermo Galactic SPC)
     *
     */
    public String saveToSpc(float[] data, Map<String, String> meta,
                            double laserFreq, double startWave, double stopWave, String dirPath) throws IOException {

        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dirPath);
        }

        LocalDateTime now = LocalDateTime.now();
        String filePath = dirPath + (dirPath.endsWith("/") || dirPath.endsWith("\\") ? "" : File.separator)
                + "Sample_" + now.format(FORMATTER) + ".spc";

        // 1. 获取硬件偏移量
        int spcStart = 0;
        if (meta != null && meta.containsKey(INST_SPC_START)) {
            Matcher m = Pattern.compile("(-?\\d+)").matcher(meta.get(INST_SPC_START));
            if (m.find()) {
                spcStart = Integer.parseInt(m.group(1));
            }
        }

        // 2. 计算物理真实步长
        double realStepWave = laserFreq / data.length;

        // 3. 物理索引对齐算法
        int indexStart = (int) Math.floor(startWave / realStepWave) - spcStart;
        int indexStop = (int) Math.ceil(stopWave / realStepWave) - spcStart;

        indexStart = Math.max(0, indexStart);
        indexStop = Math.min(data.length - 1, indexStop);

        // 4. 计算 SPC 头文件所需的参数
        int fnpts = indexStop - indexStart + 1;
        if (fnpts <= 0) {
            return null;
        }

        double ffirst = (spcStart + indexStart) * realStepWave;
        double flast = (spcStart + indexStop) * realStepWave;

        // 动态计算日志块(Metadata)的绝对偏移量: 512 + 32 + (fnpts * 4)
        int logOffset = 512 + 32 + (fnpts * 4);

        try (FileOutputStream fos = new FileOutputStream(filePath);
             FileChannel channel = fos.getChannel()) {

            // ================= 1. 写入 SPC 主头部 (512 字节) =================
            ByteBuffer mainHeader = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            mainHeader.put(0, (byte) 0);        // ftflgs
            mainHeader.put(1, (byte) 0x4B);     // fversn: 75
            mainHeader.put(2, (byte) 2);        // fexper: FT-IR
            mainHeader.put(3, (byte) 0x80);     // fexp: 128 (32-bit Float)
            mainHeader.putInt(4, fnpts);        // fnpts
            mainHeader.putDouble(8, ffirst);    // ffirst
            mainHeader.putDouble(16, flast);    // flast
            mainHeader.putInt(24, 1);           // fnsub
            mainHeader.put(28, (byte) 1);       // fxtype: 1 (Wavenumbers)
            mainHeader.put(29, (byte) 0);       // fytype: 0 (Arbitrary - 对齐ABB官方无 \f)

            // 【优化点】写入符合 Galactic 规范的打包日期 (fdate)
            int fdate = (now.getYear() << 20) | (now.getMonthValue() << 16) | (now.getDayOfMonth() << 11)
                    | (now.getHour() << 6) | now.getMinute();
            mainHeader.putInt(32, fdate);

            // 写入分辨率 (可选，填充到头部 36 字节处)
            if (meta != null && meta.containsKey("Resolution")) {
                byte[] resBytes = meta.get("Resolution").getBytes(StandardCharsets.US_ASCII);
                for (int i = 0; i < Math.min(resBytes.length, 8); i++) {
                    mainHeader.put(36 + i, resBytes[i]);
                }
            }

            mainHeader.putInt(252, logOffset);  // flogoff: 日志块偏移地址
            channel.write(mainHeader);

            // ================= 2. 写入子文件头部 (32 字节) =================
            ByteBuffer subHeader = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
            subHeader.put(0, (byte) 0);         // subflgs
            subHeader.put(1, (byte) 0x80);      // subexp: 128 (32-bit Float)
            subHeader.putShort(2, (short) 0);   // subindx
            subHeader.putInt(16, fnpts);        // subnpts
            channel.write(subHeader);

            // ================= 3. 写入真实数据体 =================
            ByteBuffer dataBuffer = ByteBuffer.allocate(fnpts * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = indexStart; i <= indexStop; i++) {
                // 直接压入 IEEE-754 原生浮点数，保持极限精度
                dataBuffer.putFloat(data[i]);
            }
            dataBuffer.flip();
            channel.write(dataBuffer);

            // ================= 4. 写入日志块 (Log Block) =================
            if (meta != null && !meta.isEmpty()) {
                StringBuilder logText = new StringBuilder();
                for (Map.Entry<String, String> entry : meta.entrySet()) {
                    String val = entry.getValue();
                    //只写入有效值，丢弃所有空值字段
                    if (val != null && !val.trim().isEmpty()) {
                        logText.append(entry.getKey()).append("=").append(val.trim()).append("\r\n");
                    }
                }

                byte[] logBytes = logText.toString().getBytes(StandardCharsets.US_ASCII);

                int logHeaderSize = 64;
                int rawLogSize = logHeaderSize + logBytes.length + 1; // +1 用于 \0 结尾符

                // 【优化点】保证日志块总大小为 4 字节对齐 (SPC 数据存储规范)
                int padding = (4 - (rawLogSize % 4)) % 4;
                int logSize = rawLogSize + padding;

                ByteBuffer logHeader = ByteBuffer.allocate(logHeaderSize).order(ByteOrder.LITTLE_ENDIAN);
                logHeader.putInt(0, logSize);       // logsizd
                logHeader.putInt(4, logSize);       // logsizm
                logHeader.putInt(8, logHeaderSize); // logtxto
                channel.write(logHeader);

                ByteBuffer logTextBuffer = ByteBuffer.allocate(logBytes.length + 1 + padding);
                logTextBuffer.put(logBytes);
                logTextBuffer.put((byte) 0); // 追加 \0 (NUL) 作为字符串截断符
                // 剩余的 padding 字节自动被 Buffer 初始化为 0
                logTextBuffer.flip();
                channel.write(logTextBuffer);
            }

            return filePath;
        }
    }

    /**
     * 提取精准对齐后的光谱数据数组，专供 C++/算法层调用
     * * @param data      底层采集的原始 float 数组
     *
     * @param meta      全量状态元数据 (用于提取偏移量)
     * @param laserFreq 激光频率
     * @param startWave 目标起始波数
     * @param stopWave  目标终止波数
     * @return double[][] 二维数组。
     * [0] -> X 轴数据 (Wavenumber)数组
     * [1] -> Y 轴数据 (Intensity)数组
     */
    public double[][] getSpectrumDataArray(float[] data, Map<String, String> meta,
                                           double laserFreq, double startWave, double stopWave) {

        // 1. 获取硬件偏移量 (保持与其他方法的绝对一致性)
        int spcStart = 0;
        if (meta != null && meta.containsKey(INST_SPC_START)) {
            Matcher m = Pattern.compile("(-?\\d+)").matcher(meta.get(INST_SPC_START));
            if (m.find()) {
                spcStart = Integer.parseInt(m.group(1));
            }
        }

        // 2. 计算物理真实步长
        double realStepWave = laserFreq / data.length;

        // 3. 物理索引对齐算法
        int indexStart = (int) Math.floor(startWave / realStepWave) - spcStart;
        int indexStop = (int) Math.ceil(stopWave / realStepWave) - spcStart;

        indexStart = Math.max(0, indexStart);
        indexStop = Math.min(data.length - 1, indexStop);

        int fnpts = indexStop - indexStart + 1;

        // 如果没有有效数据，返回空的二维数组防空指针
        if (fnpts <= 0) {
            return new double[2][0];
        }

        // 4. 构建供 C++ 使用的独立 X 和 Y 数组
        double[] xArray = new double[fnpts];
        double[] yArray = new double[fnpts];

        for (int i = 0; i < fnpts; i++) {
            int actualIndex = indexStart + i;

            // 计算精准的 X 轴坐标
            xArray[i] = (spcStart + actualIndex) * realStepWave;
            // 获取 Y 轴能量值，自动从 float 提升精度至 double
            yArray[i] = data[actualIndex];
        }

        // 返回包含两个连续内存块的二维数组，完美适配 JNI
        return new double[][]{xArray, yArray};
    }
}
