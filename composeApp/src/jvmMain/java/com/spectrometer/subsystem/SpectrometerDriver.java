package com.spectrometer.subsystem;

import com.spectrometer.config.SpectrometerProperties;
import com.spectrometer.driver.AcquisitionDriverClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public class SpectrometerDriver {
    private static final Logger log = LoggerFactory.getLogger(SpectrometerDriver.class);
    private final SpectrometerProperties props;
    public AcquisitionDriverClient client;
    private AcquisitionDriverClient.BoardInformation boardInfo;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean tcpConnected = false;
    private volatile boolean boardOpened = false;

    public static final int SOURCE_FIFO = 0;
    public static final int SOURCE_CURRENT = 1;

    public SpectrometerDriver(SpectrometerProperties props) {
        this.props = props;
    }

    public boolean connectTcp() {
        lock.lock();
        try {
            if (client != null) {
                client.close();
            }
            client = new AcquisitionDriverClient(props.getServerIp(), props.getTcpPort());
            log.info("尝试建立 TCP 连接: {}:{}", props.getServerIp(), props.getTcpPort());
            if (client.open()) {
                log.error("无法建立与光谱仪服务器的 TCP 连接");
                tcpConnected = false;
                return false;
            }
            log.info("TCP 连接成功!");
            tcpConnected = true;
            return true;
        } catch (Exception e) {
            log.error("TCP 连接异常: {}", e.getMessage());
            tcpConnected = false;
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean openBoard() {
        lock.lock();
        try {
            if (!tcpConnected || client == null) {
                log.error("请先建立 TCP 连接");
                return false;
            }
            log.info("正在获取板卡信息并初始化: {}", props.getBoardName());
            boardInfo = client.openBoard(
                    props.getBoardName(), props.getLaserFreq(), 0, 5000, props.getUdpPort()
            );

            if (boardInfo == null) {
                log.error("硬件 OpenBoard 失败，未获取到板卡信息");
                boardOpened = false;
                return false;
            }

            log.info("板卡初始化成功！驱动版本: {}", boardInfo.acquisitionDriverVersion);
            boardOpened = true;
            return true;
        } catch (Exception e) {
            log.error("OpenBoard 异常: {}", e.getMessage());
            boardOpened = false;
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void configure(short res, short gain1, short gain2, float startW, float stopW) throws IOException {
        lock.lock();
        try {
            if (!boardOpened) throw new IOException("板卡未打开，无法下发配置");
            client.setResolution(res);
            client.setGainValues(gain1, gain2);
            client.setDataType((byte) 1, (byte) 0, startW, stopW);
            if (client.startAlign() != AcquisitionDriverClient.STATE_SUCCESS) {
                throw new IOException("启动连续采集模式失败");
            }
            log.info("参数下发并启动对齐模式成功");
        } finally {
            lock.unlock();
        }
    }

    public void startCoaddition(int scans, int runs) throws IOException {
        lock.lock();
        try {
            if (!boardOpened) throw new IOException("板卡未打开");
            int state = client.setCoadditionCommand((short) 1, runs, scans, 1, (short) 1, 0.0f, 0.0f);
            if (state != AcquisitionDriverClient.STATE_SUCCESS) {
                throw new IOException("设置累加指令失败，硬件返回代码: " + state);
            }
        } finally {
            lock.unlock();
        }
    }

    public void stopAcquisition() {
        lock.lock();
        try {
            if (client != null && boardOpened) {
                client.stopAcqusition();
                log.info("已下发停止采集指令");
            }
        } catch (Exception e) {
            log.error("停止采集指令下发异常: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public ByteBuffer fetchControlStatus() throws IOException {
        lock.lock();
        try { return boardOpened ? client.getControlStatus() : null; } finally { lock.unlock(); }
    }

    public ByteBuffer fetchStatusDefinition() throws IOException {
        lock.lock();
        try { return boardOpened ? client.getStatusDefinition() : null; } finally { lock.unlock(); }
    }

    public ByteBuffer fetchCurrentStatus() throws IOException {
        lock.lock();
        try { return boardOpened ? client.getStatus((byte) 0) : null; } finally { lock.unlock(); }
    }

    public ByteBuffer fetchHealthStatusDefinition() throws IOException {
        lock.lock();
        try { return boardOpened ? client.acqGetHealthMonitoringStatusDefinition() : null; } finally { lock.unlock(); }
    }

    public ByteBuffer fetchHealthStatus() throws IOException {
        lock.lock();
        try { return boardOpened ? client.getHealthMonitoringStatus() : null; } finally { lock.unlock(); }
    }

    public float[] fetchRawData(int source, int nPts, long timeoutMs) throws IOException, InterruptedException {
        lock.lock();
        try {
            if (!boardOpened) throw new IOException("设备未连接");
            float[] data = new float[nPts];
            long startTime = System.currentTimeMillis();
            int state = client.getData(source, nPts, data);

            while (state != AcquisitionDriverClient.STATE_SUCCESS) {
                if (System.currentTimeMillis() - startTime > timeoutMs) throw new IOException("读取光谱数据超时");
                Thread.sleep(20);
                state = client.getData(source, nPts, data);
            }
            return data;
        } finally {
            lock.unlock();
        }
    }

    public void disconnect() {
        lock.lock();
        try {
            if (client != null) {
                try { client.stopAcqusition(); } catch (Exception ignored) {}
                try { client.closeBoard(); } catch (Exception ignored) {}
                try { client.close(); } catch (Exception ignored) {}
            }
            tcpConnected = false;
            boardOpened = false;
            boardInfo = null;
        } finally {
            client = null;
            lock.unlock();
        }
    }

    public boolean isTcpConnected() { return tcpConnected; }
    public boolean isBoardOpened() { return boardOpened; }
    public boolean isConnected() { return boardOpened; }
    public AcquisitionDriverClient.BoardInformation getBoardInfo() { return boardInfo; }

    /**
     * 遵循 TestClient 官方逻辑，通过板卡信息表自动获取第一块板卡的 Instrument Name
     */
    public String autoDetectBoardName() {
        try {
            if (client != null) {
                // 1. 获取设备数量
                int count = client.acqGetBoardCount();
                if (count > 0) {
                    // 2. 拉取设备信息表
                    ByteBuffer boardInfo = client.acqGetBoardInfo(count);
                    if (boardInfo != null && boardInfo.capacity() >= 64) {
                        boardInfo.position(0); // 读取第一块板卡 (i=0)

                        // 3. 提取前 64 字节
                        byte[] nameBytes = new byte[64];
                        boardInfo.get(nameBytes);

                        // 4. ABB 通信规范底层字符使用 UTF-16LE 编码
                        String name = new String(nameBytes, "UTF-16LE");

                        // 5. C++ 字符串通过 \0 结尾，需要截断冗余的空字符
                        int nullIdx = name.indexOf('\0');
                        if (nullIdx >= 0) {
                            name = name.substring(0, nullIdx);
                        }
                        return name.trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("自动获取板卡名称失败: " + e.getMessage());
        }
        return "";
    }
}