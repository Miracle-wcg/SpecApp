package com.trionesdev.oca.core.shared.spectrometer.subsystem;

import com.trionesdev.oca.core.shared.spectrometer.driver.StatusDefinition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class MetadataParser {

    public static final String INST_SPC_NPTS = "Inst. Spc Npts";
    private StatusDefinition statusTable;
    private StatusDefinition healthStatusTable;
    private int[] controlStatusIndex;

    public void initTable(ByteBuffer statusDefBuffer) {
        statusTable = parseDefinitionTable(statusDefBuffer);
    }

    public void initHealthTable(ByteBuffer healthDefBuffer) {
        healthStatusTable = parseDefinitionTable(healthDefBuffer);
    }

    /**
     * 【新增】：初始化控制状态表映射 (Cmd 38 返回的结构)
     */
    public void initControlTable(ByteBuffer ctrlBuf) {
        if (ctrlBuf == null) {
            return;
        }
        ctrlBuf.order(ByteOrder.LITTLE_ENDIAN);
        ctrlBuf.position(0);
        float version = ctrlBuf.getFloat();
        int nbStatus = ctrlBuf.getInt();
        controlStatusIndex = new int[nbStatus];
        for (int i = 0; i < nbStatus; i++) {
            controlStatusIndex[i] = ctrlBuf.getInt();
        }
    }

    private StatusDefinition parseDefinitionTable(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        buffer.position(0);
        float version = buffer.getFloat();
        int size = buffer.getInt();
        StatusDefinition table = new StatusDefinition(size, version);

        for (int i = 0; i < size; i++) {
            table.status[i].name = table.getString(buffer, StatusDefinition.NAME_MAX_SIZE);
            table.status[i].unit = table.getString(buffer, StatusDefinition.UNIT_MAX_SIZE);
            table.status[i].type = buffer.get();
            table.status[i].propertyType = buffer.get();
            table.status[i].length = buffer.getInt();
            table.status[i].index = buffer.getInt();
            table.status[i].hidden = buffer.get();
        }
        return table;
    }

    public int extractNpts(ByteBuffer statusBuffer) {
        if (statusTable == null || statusBuffer == null) {
            return 0;
        }
        for (int i = 0; i < statusTable.size; i++) {
            if (INST_SPC_NPTS.equalsIgnoreCase(statusTable.status[i].name.trim())) {
                statusBuffer.position(statusTable.status[i].index);
                return statusBuffer.getInt();
            }
        }
        return 0;
    }

    /**
     * 【新增】：精准提取控制状态数值 (如 Coadd State)
     */
    public Number extractControlValue(ByteBuffer statusBuf, int controlId) {
        if (controlStatusIndex == null || controlId < 0 || controlId >= controlStatusIndex.length) {
            return 0;
        }
        int tableIndex = controlStatusIndex[controlId];
        if (statusTable == null || tableIndex < 0 || tableIndex >= statusTable.size) {
            return 0;
        }
        StatusDefinition.Status stat = statusTable.status[tableIndex];
        return extractNumericValue(statusBuf, stat);
    }

    public Map<String, String> parseDynamicMetadata(ByteBuffer statusBuffer) {
        Map<String, String> result = new LinkedHashMap<>();
        if (statusTable == null || statusBuffer == null) {
            return result;
        }

        for (int i = 0; i < statusTable.size; i++) {
            StatusDefinition.Status stat = statusTable.status[i];
            if (stat.name == null || stat.name.trim().isEmpty()) {
                continue;
            }
            result.put(stat.name.trim(), extractValueStr(statusBuffer, stat));
        }
        return result;
    }

    public Map<String, Object> parseHealthMonitoring(ByteBuffer defBuf, ByteBuffer statusBuf) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (defBuf == null || statusBuf == null) {
            return report;
        }

        defBuf.order(ByteOrder.LITTLE_ENDIAN);
        statusBuf.order(ByteOrder.LITTLE_ENDIAN);

        defBuf.position(0);
        float version = defBuf.getFloat();
        int groupNumber = defBuf.getInt();

        for (int i = 0; i < groupNumber; i++) {
            int itemNumber = defBuf.getInt();
            String groupName = getHealthString(defBuf, 64);
            int groupIndex = defBuf.getInt();

            if (groupName.toLowerCase().contains("ir source")) {
                for (int j = 0; j < itemNumber; j++) {
                    getHealthString(defBuf, 64);
                    defBuf.getInt();
                }
                continue;
            }

            byte groupState = 0;
            if (groupIndex >= 0 && groupIndex < statusBuf.limit()) {
                statusBuf.position(groupIndex);
                groupState = statusBuf.get();
            }

            Map<String, Object> groupData = new LinkedHashMap<>();
            groupData.put("state", (int) groupState);
            groupData.put("isHealthy", groupState == 0);

            Map<String, String> items = new LinkedHashMap<>();
            for (int j = 0; j < itemNumber; j++) {
                String itemName = getHealthString(defBuf, 64);
                int itemIndex = defBuf.getInt();

                byte itemState = 0;
                if (itemIndex >= 0 && itemIndex < statusBuf.limit()) {
                    statusBuf.position(itemIndex);
                    itemState = statusBuf.get();
                }
                items.put(itemName, String.valueOf(itemState));
            }
            groupData.put("details", items);
            report.put(groupName, groupData);
        }
        return report;
    }

    private String extractValueStr(ByteBuffer buffer, StatusDefinition.Status stat) {
        if (stat.index < 0 || stat.index >= buffer.limit()) return "N/A";

        buffer.position(stat.index);
        if (stat.length <= 1) {
            return String.valueOf(extractNumericValue(buffer, stat));
        } else {
            int readLen = Math.min(stat.length, buffer.limit() - stat.index);
            byte[] arr = new byte[readLen];
            buffer.get(arr);
            int actualLen = 0;
            while (actualLen < arr.length && arr[actualLen] != 0) actualLen++;
            return new String(arr, 0, actualLen, StandardCharsets.UTF_8).trim();
        }
    }

    private Number extractNumericValue(ByteBuffer buffer, StatusDefinition.Status stat) {
        int required = getDataTypeSize(stat.type);
        if (stat.index < 0 || (stat.index + required) > buffer.limit()) return 0;

        buffer.position(stat.index);
        switch (stat.type) {
            case StatusDefinition.TYPE_BYTE:
                return buffer.get();
            case StatusDefinition.TYPE_SHORT:
                return buffer.getShort();
            case StatusDefinition.TYPE_INT:
                return buffer.getInt();
            case StatusDefinition.TYPE_FLOAT:
                return buffer.getFloat();
            case StatusDefinition.TYPE_DOUBLE:
                return buffer.getDouble();
            default:
                return 0;
        }
    }

    private int getDataTypeSize(byte type) {
        switch (type) {
            case StatusDefinition.TYPE_BYTE:
                return 1;
            case StatusDefinition.TYPE_SHORT:
                return 2;
            case StatusDefinition.TYPE_INT:
            case StatusDefinition.TYPE_FLOAT:
                return 4;
            case StatusDefinition.TYPE_DOUBLE:
                return 8;
            default:
                return 0;
        }
    }

    private String getHealthString(ByteBuffer buf, int nbrBytes) {
        byte[] tmp = new byte[nbrBytes];
        if (buf.remaining() < nbrBytes) return "Unknown";

        buf.get(tmp);
        int length = 0;
        for (; length < nbrBytes - 1; length += 2) {
            if (tmp[length] == 0 && tmp[length + 1] == 0) break;
        }
        try {
            return new String(tmp, 0, length, "UTF-16LE").trim();
        } catch (Exception e) {
            return "";
        }
    }
}
