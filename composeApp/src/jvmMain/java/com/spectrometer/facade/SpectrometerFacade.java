package com.spectrometer.facade;

import com.spectrometer.config.SpectrometerProperties;
import com.spectrometer.model.SpectrumResult;
import com.spectrometer.subsystem.MetadataParser;
import com.spectrometer.subsystem.SpectrometerDriver;
import com.spectrometer.subsystem.SpectrumStorage;
import com.trionesdev.oca.core.shared.spectrometer.utils.OSPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class SpectrometerFacade {

    private static final Logger log = LoggerFactory.getLogger(SpectrometerFacade.class);

    private final SpectrometerProperties props;
    private final SpectrometerDriver driver;
    private final MetadataParser parser;
    private final SpectrumStorage storage;

    public SpectrometerFacade(SpectrometerProperties props, SpectrometerDriver driver, MetadataParser parser, SpectrumStorage storage) {
        this.props = props;
        this.driver = driver;
        this.parser = parser;
        this.storage = storage;
    }

    public void startup() {
        startSystem();
    }

    public void shutdown() {
        driver.disconnect();
    }

    public boolean startSystem() {
        try {
            // 【修复点】：使用新的两步握手逻辑替换废弃的 connectAndInit()
            if (!driver.connectTcp()) {
                log.error("Facade 系统初始化失败：TCP 握手未通过");
                return false;
            }
            if (!driver.openBoard()) {
                log.error("Facade 系统初始化失败：打开板卡失败");
                return false;
            }

            ByteBuffer defBuffer = driver.fetchStatusDefinition();
            if (defBuffer == null) throw new RuntimeException("无法获取 Status Definition");
            parser.initTable(defBuffer);

            ByteBuffer ctrlBuffer = driver.fetchControlStatus();
            if (ctrlBuffer != null) parser.initControlTable(ctrlBuffer);

            ByteBuffer healthDefBuffer = driver.fetchHealthStatusDefinition();
            if (healthDefBuffer != null) parser.initHealthTable(healthDefBuffer);

            SpectrometerProperties.Params p = props.getParams();
            driver.configure(p.getResolution(), p.getFirstGain(), p.getSecondGain(), p.getStartWave(), p.getStopWave());

            log.info("光谱仪 Facade 系统启动成功！");
            return true;
        } catch (Exception e) {
            log.error("Facade 系统启动致命异常: {}", e.getMessage());
            driver.disconnect();
            return false;
        }
    }

    public SpectrumResult collectSingleFrameAndSave() {
        try {
            if (!driver.isConnected() && !startSystem()) return buildErrorResult("设备脱机");

            ByteBuffer statusBuf = driver.fetchCurrentStatus();
            int nPts = parser.extractNpts(statusBuf);
            if (nPts <= 0) throw new IllegalStateException("数据点数异常: " + nPts);

            float[] data = driver.fetchRawData(SpectrometerDriver.SOURCE_CURRENT, nPts, props.getAutoCollect().getTimeoutMs());

            Map<String, String> metadata = parser.parseDynamicMetadata(statusBuf);
            metadata.put("Comment", "Live Preview - 1 Scan");
            metadata.put("ObjectID", UUID.randomUUID().toString().toUpperCase());

            String configPath = getConfigSavePath();

            String savedPath = storage.saveToSpc(data, metadata, props.getLaserFreq(),
                    props.getParams().getStartWave(), props.getParams().getStopWave(), configPath);

            return SpectrumResult.builder().success(true).data(data).metadata(metadata).timestamp(LocalDateTime.now()).savedFilePath(savedPath).build();

        } catch (Exception e) {
            log.error("实时预览采集失败: {}", e.getMessage());
            driver.disconnect();
            return buildErrorResult(e.getMessage());
        }
    }

    public SpectrumResult collectAveragedFrameAndSave() {
        try {
            if (!driver.isConnected() && !startSystem()) return buildErrorResult("设备脱机");

            int numScans = props.getParams().getNumScans();
            int numRuns = props.getParams().getNumRuns();

            driver.startCoaddition(numScans, numRuns);
            log.info("已启动正式累加采集，要求扫描次数: {}", numScans);

            long t0 = System.currentTimeMillis();
            long timeout = props.getAutoCollect().getTimeoutMs() + (numScans * 1500L);

            while (true) {
                ByteBuffer statusBuf = driver.fetchCurrentStatus();
                if (statusBuf == null) throw new IllegalStateException("无法获取状态信息");

                int coaddState = parser.extractControlValue(statusBuf, 11).intValue();

                if (coaddState == 0) {
                    break;
                }

                if (System.currentTimeMillis() - t0 > timeout) {
                    throw new IllegalStateException("累加扫描执行超时!");
                }
                Thread.sleep(300);
            }

            ByteBuffer statusBuf = driver.fetchCurrentStatus();
            int nPts = parser.extractNpts(statusBuf);
            if (nPts <= 0) throw new IllegalStateException("点数异常");

            float[] data = driver.fetchRawData(SpectrometerDriver.SOURCE_FIFO, nPts, props.getAutoCollect().getTimeoutMs());

            Map<String, String> metadata = parser.parseDynamicMetadata(statusBuf);
            metadata.put("Comment", "Official Coaddition Measure - " + numScans + " Scans Averaged");
            metadata.put("ObjectID", UUID.randomUUID().toString().toUpperCase());

            String configPath = getConfigSavePath();

            String savedPath = storage.saveToSpc(data, metadata, props.getLaserFreq(),
                    props.getParams().getStartWave(), props.getParams().getStopWave(), configPath);

            log.info("正式采样完毕，已生成优质光谱数据: {}", savedPath);

            return SpectrumResult.builder().success(true).data(data).metadata(metadata).timestamp(LocalDateTime.now()).savedFilePath(savedPath).build();

        } catch (Exception e) {
            log.error("累加采集执行失败: {}", e.getMessage());
            driver.disconnect();
            return buildErrorResult(e.getMessage());
        }
    }

    private String getConfigSavePath() {
        OSPathUtils.OSType osType = OSPathUtils.getOperatingSystem();
        String configPath = switch (osType) {
            case WINDOWS -> props.getSavePathWindows();
            case LINUX, MACOS, UNKNOWN -> props.getSavePath();
        };
        return configPath;
    }

    public Map<String, Object> checkHealthMonitoring() {
        try {
            if (!driver.isConnected() && !startSystem()) return null;
            ByteBuffer hDef = driver.fetchHealthStatusDefinition();
            ByteBuffer hStat = driver.fetchHealthStatus();
            if (hDef == null || hStat == null) return null;
            return parser.parseHealthMonitoring(hDef, hStat);
        } catch (Exception e) {
            log.error("健康状态获取异常", e);
            return null;
        }
    }

    public boolean forceReconnect() {
        driver.disconnect();
        return startSystem();
    }

    public boolean isSystemConnected() {
        return driver.isConnected();
    }

    private SpectrumResult buildErrorResult(String message) {
        return SpectrumResult.builder().success(false).message(message).timestamp(LocalDateTime.now()).build();
    }
}