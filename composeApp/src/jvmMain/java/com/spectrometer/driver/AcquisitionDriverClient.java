/* AcquisitionDriverClient.java
 *---------------------- Copyright (c) ABB Bomem inc, 2006 -------------------
 *                                 Source code
 * This software is the property of Bomem and should be considered and treated
 * as proprietary information.  Refer to the "Source Code License Agreement"
 *----------------------------------------------------------------------------
 *
 * $Log:   M:/VM8/VMPROJECT/GENE/archives/AcquisitionDriverClient/JavaCode/Src/com/bomem/gene/driverClient/AcquisitionDriverClient.java-arc  $
 *
 *    Rev 1.11   Nov 24 2008 08:39:08   cmbourqu
 * Update!
 *
 *    Rev 1.31   Aug 01 2008 12:08:42   cmbourqu
 * Implement getValidationTestParameter.
 *
 *    Rev 1.30   Jul 18 2008 07:04:10   cmbourqu
 * Put delayBefore in first.
 *
 *    Rev 1.29   Jul 18 2008 07:00:34   cmbourqu
 * Add setValidationInitialReference and setValidationTestParameter.
 * Add delayBetween and delayBefore in setCoadditionCommand.
 *
 *    Rev 1.28   Jul 09 2008 07:28:16   cmbourqu
 * Add numberOfMeasurement in setCoadditionCommand.
 *
 *    Rev 1.27   Jun 23 2008 14:39:32   cmbourqu
 * Add getStatus (byte bufferType).
 *
 *    Rev 1.26   Feb 25 2008 15:10:16   cmbourqu
 * Add getControlStatus method (needed to new housekeeping status generic support)
 *
 *    Rev 1.25   Oct 25 2007 09:55:02   cmbourqu
 * Update RX buffer to 2MB (Validation mode 1cm-1)!
 *
 *    Rev 1.24   Aug 30 2007 10:53:10   cmbourqu
 * Update public BoardInformation openBoard (String boardName, double laser, int bufSize, int timeout, int udpPortNumber):
 *
 * board name length not limited to 32 characters!
 *
 *    Rev 1.23   Aug 30 2007 09:59:10   cmbourqu
 * Implement method: openBoardBoardNameLengthVariable for board name not limited by the length.
 *
 *    Rev 1.22   Jun 22 2007 15:47:30   cmbourqu
 * Add getDriverVersionNumber command.
 *
 *    Rev 1.21   May 31 2007 21:25:26   cmbourqu
 * Set position(0) for getValidationResult and getValidationData.
 *
 *    Rev 1.20   May 31 2007 10:21:12   cmbourqu
 * Adjust string length for setValidationTest method.
 *
 *    Rev 1.19   May 25 2007 11:42:48   cmbourqu
 * Implementation of validation mode.
 *
 *    Rev 1.18   May 17 2007 16:24:58   cmbourqu
 * Implement runValidationTest command.
 *
 *    Rev 1.17   May 17 2007 11:09:54   cmbourqu
 * Add command 31: setValidationTest
 *
 *    Rev 1.16   Mar 02 2007 15:43:44   cmbourqu
 * Implement sqcSpectralQuality.
 *
 *    Rev 1.15   Feb 27 2007 10:55:58   cmbourqu
 * Add commands 28-29-30: Get Board Count and Open Board with UDP port number.
 *
 *    Rev 1.14   Feb 12 2007 08:52:44   cmbourqu
 * Move the code in the DriverCommand.
 *
 *    Rev 1.13   Feb 08 2007 22:09:48   cmbourqu
 * Add getBoardName command.
 *
 *    Rev 1.12   Feb 02 2007 08:28:26   cmbourqu
 * Add extendedConfigurationRequest command.
 *
 *    Rev 1.11   Jan 25 2007 11:37:46   cmbourqu
 * In getData, compare the size of data with npts before copy data.
 *
 *    Rev 1.10   Jan 24 2007 20:51:42   cmbourqu
 * Determine type and phase!
 *
 *    Rev 1.9   Jan 24 2007 14:26:18   cmbourqu
 * Add phaseCorrectionFlag in setDataType command.
 *
 *    Rev 1.8   Jan 19 2007 07:51:32   cmbourqu
 * TeC Power answer-> cmd = 23
 *
 *    Rev 1.7   Jan 18 2007 07:20:10   cmbourqu
 * setTECPower command ID = 23.
 *
 *    Rev 1.6   Jan 17 2007 10:00:30   cmbourqu
 * Add new commands!
 *
 *    Rev 1.5   Jan 11 2007 09:37:02   cmbourqu
 * Update getHealthMonitoringStatus ();
 *
 *    Rev 1.4   Jan 11 2007 09:09:30   cmbourqu
 *  Update getHealthMonitoringStatus ().
 *
 *    Rev 1.3   Jan 10 2007 14:12:52   cmbourqu
 * Reset position of buffer in acqGetBoardInfo method.
 *
 *    Rev 1.2   Jan 10 2007 12:25:30   cmbourqu
 * Debug new commands.
 *
 *    Rev 1.1   Jan 10 2007 10:52:52   cmbourqu
 * Add getHealthMonitoringStatusDefinition (), getBoardCount () and getBoardInfo () methods.
 *
 *    Rev 1.0   Jan 10 2007 09:01:34   cmbourqu
 * Initial revision.
 */

package com.trionesdev.oca.core.shared.spectrometer.driver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.SocketChannel;

/**
 * Manages communication with AcquisitionDriverServer.
 * <p>
 * Notes:
 * </p>
 * <p>
 * </p>
 *
 * @author cmbourqu
 * @version $Revision:   1.11  $
 */
public class AcquisitionDriverClient {
    private final static int COMMAND_HEADER = 0xf0f0f0f0;
    private final static int RESULT_HEADER = 0xf0f0f0f1;

    /**
     * Command request size in bytes.
     */
    private static final int CMD_SIZE = 1024;
    private static final int RX_BUF_LEN = (1024 * 1024 * 2);

    public final static int STATE_SUCCESS = 0;
    public final static int STATE_FAILURE = 1;

    /**
     * Name of the associated Client.
     */
    private String name;
    /**
     * TCP/IP port of the associated Client.
     */
    private int port;
    /**
     * Associated SocketChannel when Client connection is opened.
     */
    private SocketChannel client;

    /**
     * <code>timeout</code> for command that shall be fast like <code>getBatchProperties</code>.
     */
    private final int shortTimeout = 100000;

    private ByteBuffer cmd;
    private ByteBuffer answer;

    private BoardInformation boardInformation;

    /**
     * Constructs a <code>AcquisitionDriverClient</code>.
     *
     * @param name the <code>String</code> Client name.
     * @param port the TCP/IP port.
     */
    public AcquisitionDriverClient(String name, int port) {
        this.name = name;
        this.port = port;

        boardInformation = new BoardInformation();

    }

    /**
     * Returns name of this <code>Client</code>.
     */
    public String getName() {
        return name + ":" + port;
    }

    /**
     * Connects to Acquisition Driver Server.
     */
    public boolean open() {
        try {
            client = SocketChannel.open(new InetSocketAddress(name, port));
            cmd = ByteBuffer.allocate(CMD_SIZE);
            cmd.order(ByteOrder.LITTLE_ENDIAN);
            answer = ByteBuffer.allocate(RX_BUF_LEN);
            answer.order(ByteOrder.LITTLE_ENDIAN);
        } catch (UnknownHostException ex) {
            return true;
        } catch (IOException ex) {
            return true;
        }
        return false;
    }

    /**
     * Closes to Acquisition Driver Server.
     */
    public void close() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (IOException ex) {
            // Ignore it, we want to close it anyway.
        }
        client = null;
    }

    /*
     * openBoard command
     *
     * @param	boardName		name of the instrument to acquire
     * @param	laser			laser wavelength (maybe desappears)
     * @param	bufSize			size of the FIFO  destined to the acquisition (0=default=50MBytes)
     * @param	timeout			...
     *
     * @return  BoardInformation structure
     */
    public BoardInformation openBoard(String boardName, double laser, int bufSize, int timeout)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(0);
        cmd.putInt(48);
        putString(cmd, boardName, 32, 32);
        cmd.putDouble(laser);
        cmd.putInt(bufSize);
        cmd.putInt(timeout);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(26);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 0 || len != 10) {
            throw new IOException("Invalid answer");
        }

        if (state == 0) {
            float structureVersion = answer.getFloat();
            byte instrumentType = answer.get();
            byte maximumChannel = answer.get();
            float acquisitionDriverVersion = answer.getFloat();
            ;

            boardInformation.set(structureVersion, instrumentType, maximumChannel, acquisitionDriverVersion);

            return boardInformation;
        } else {
            return null;
        }
    }

    /*
     * openBoard command
     *
     * @param	boardName		name of the instrument to acquire
     * @param	laser			laser wavelength (maybe desappears)
     * @param	bufSize			size of the FIFO  destined to the acquisition (0=default=50MBytes)
     * @param	timeout			...
     * @param	udpPortNumber	UDP port number to used by the instrument or Simulator
     *
     * @return  BoardInformation structure
     */
    public BoardInformation openBoard(String boardName, double laser, int bufSize, int timeout, int udpPortNumber)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(29);

        // String in UTF-16LE format (length =* 2)
        cmd.putInt((boardName.length() * 2) + 20);
        putString(cmd, boardName, boardName.length() * 2, boardName.length() * 2);
        cmd.putDouble(laser);
        cmd.putInt(bufSize);
        cmd.putInt(timeout);
        cmd.putInt(udpPortNumber);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(26);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 29 || len != 10) {
            throw new IOException("Invalid answer");
        }

        if (state == 0) {
            float structureVersion = answer.getFloat();
            byte instrumentType = answer.get();
            byte maximumChannel = answer.get();
            float acquisitionDriverVersion = answer.getFloat();
            ;

            boardInformation.set(structureVersion, instrumentType, maximumChannel, acquisitionDriverVersion);

            return boardInformation;
        } else {
            return null;
        }
    }

    /*
     * openBoard command
     *
     * @param	laser			laser wavelength (maybe desappears)
     * @param	bufSize			size of the FIFO  destined to the acquisition (0=default=50MBytes)
     * @param	timeout			...
     * @param	boardName		name of the instrument to acquire
     * @param	udpPortNumber	UDP port number used by the instrument or simulator
     *
     * @return  BoardInformation structure
     */
    public BoardInformation openBoard(double laser, int bufSize, int timeout, String ipAddress, int udpPortNumber)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(30);
        cmd.putInt(52);
        cmd.putDouble(laser);
        cmd.putInt(bufSize);
        cmd.putInt(timeout);
        putString(cmd, ipAddress, 32, 32);
        cmd.putInt(udpPortNumber);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(26);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 30 || len != 10) {
            throw new IOException("Invalid answer");
        }

        if (state == 0) {
            float structureVersion = answer.getFloat();
            byte instrumentType = answer.get();
            byte maximumChannel = answer.get();
            float acquisitionDriverVersion = answer.getFloat();
            ;

            boardInformation.set(structureVersion, instrumentType, maximumChannel, acquisitionDriverVersion);

            return boardInformation;
        } else {
            return null;
        }
    }

    /*
     * openBoard command
     *
     * @param	laser			laser wavelength (maybe desappears)
     * @param	bufSize			size of the FIFO  destined to the acquisition (0=default=50MBytes)
     * @param	timeout			...
     * @param	boardName		name of the instrument to acquire
     *
     * @return  BoardInformation structure
     */
    public BoardInformation openBoard(double laser, int bufSize, int timeout, String ipAddress)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(25);
        cmd.putInt(48);
        cmd.putDouble(laser);
        cmd.putInt(bufSize);
        cmd.putInt(timeout);
        putString(cmd, ipAddress, 32, 32);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(26);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 25 || len != 10) {
            throw new IOException("Invalid answer");
        }

        if (state == 0) {
            float structureVersion = answer.getFloat();
            byte instrumentType = answer.get();
            byte maximumChannel = answer.get();
            float acquisitionDriverVersion = answer.getFloat();
            ;

            boardInformation.set(structureVersion, instrumentType, maximumChannel, acquisitionDriverVersion);

            return boardInformation;
        } else {
            return null;
        }
    }

    /*
     * closeBoard command
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int closeBoard()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(1);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 1 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * startAcqusition command
     *
     * @param wait		delay before measurements
     * @param scans		numbber of coaddition wanted
     * @param runs		number of measurements wanted
     * @delay			delay between each measurement
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int startAcqusition(long wait, int scans, int runs, long delay)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(2);
        cmd.putInt(24);
        cmd.putLong(wait);
        cmd.putInt(scans);
        cmd.putInt(runs);
        cmd.putLong(delay);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 2 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * stopAcqusition command
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int stopAcqusition()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(3);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 3 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * getStatusDefinition commannd
     *
     * @return ByteBuffer		contains current status definition
     *
     */
    public ByteBuffer getStatusDefinition()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(4);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 4) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        if (len > 0 && state == 0) {
            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();

            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);

            return status;
        } else {
            return null;
        }
    }

    /*
     * getStatus command
     *
     * @return ByteBuffer		contains current status of the instrument NGEN
     */
    public ByteBuffer getStatus()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(5);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 5) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);
            return status;
        } else {
            return null;
        }
    }

    /*
     * getStatus command
     *
     * @param  bufferType		0 = Normal
     * 							1 = COADD Current
     * 							2 = COADD FIFO
     *
     * @return ByteBuffer		contains current status of the instrument NGEN
     */
    public ByteBuffer getStatus(byte bufferType)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(39);
        cmd.putInt(1);
        cmd.put(bufferType);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 39) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);
            return status;
        } else {
            return null;
        }
    }

    /**
     * setValidationInitialReference
     *
     * @param id        Validation Test ID
     * @param reference buffer that contains intial Reference information
     * @return state		0 = sucess
     * 1 = failure
     *
     */
    public int setValidationInitialReference(byte testId, byte[] reference)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(40);
        cmd.putInt(reference.length + 1);
        cmd.put(testId);
        cmd.put(reference);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 40) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setValidationTestParameter
     *
     * @param id        Validation Test ID
     * @param reference buffer that contains parameter information
     * @return state  		STATE_SUCCESS/STATE_FAILURE
     *
     */
    public int setValidationTestParameter(byte testId, byte[] parameter)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(41);
        cmd.putInt(parameter.length + 1);
        cmd.put(testId);
        cmd.put(parameter);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 41) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setValidationTestParameter
     *
     * @param id Validation Test ID
     * @return ByteBuffer	contains current validation parameter value
     *
     */
    public ByteBuffer getValidationTestParameter(byte testId)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(42);
        cmd.putInt(1);
        cmd.put(testId);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 42) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);
            return status;
        } else {
            return null;
        }
    }

    /*
     * getData
     *
     * @param source		0: FIFO (Acquisition mode) 1 = current (Align mode)
     * @param nPts 			number of point of the associated resolution (will desapears...)
     * @param data			bufferto read current data
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int getData(int source, int nPts, float data[])
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(6);
        cmd.putInt(0);

        cmd.putInt(source);
        cmd.putInt(nPts);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 6) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        FloatBuffer fb = answer.asFloatBuffer();
        if (nPts <= data.length) {
            fb.get(data, 0, nPts);
        } else {
            // Scan overflow (follow a resolution or type modification)
            state = STATE_FAILURE;
        }

        return state;
    }

    /*
     * sendCommand command
     *
     * @param command ID
     * @param value
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int sendCommand(int command, short value)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(7);
        cmd.putInt(6);
        cmd.putInt(command);
        cmd.putShort(value);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 7 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * sendCommandBuffer
     *
     * @param buffet	script to send
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int sendCommandBuffer(ByteBuffer buffer)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(8);
        cmd.putInt(buffer.capacity());
        cmd.put(buffer);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 8 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * startAlign
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int startAlign()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(9);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 9 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * sqcSpectralQuality
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public ByteBuffer sqcSpectralQuality()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(10);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 10) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        if (len > 0 && state == 0) {
            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();

            ByteBuffer result = ByteBuffer.allocate(len);
            result.order(ByteOrder.LITTLE_ENDIAN);
            result.put(answer.array(), 0, len);

            result.position(0);
            return result;
        } else {
            return null;
        }
    }

    /*
     * sqcSpectralCalibration
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public ByteBuffer sqcSpectralCalibration()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(11);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 11) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        if (len > 0 && state == 0) {
            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();

            ByteBuffer result = ByteBuffer.allocate(len);
            result.order(ByteOrder.LITTLE_ENDIAN);
            result.put(answer.array(), 0, len);

            result.position(0);
            return result;
        } else {
            return null;
        }
    }

    /*
     * getHealthMonitoringStatus
     *
     * @return ByteBuffer heailthMonitoring structure (next release)
     */
    public ByteBuffer getHealthMonitoringStatus()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(12);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 12) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        if (len > 0 && state == 0) {
            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();

            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);

            status.position(0);
            return status;
        } else {
            return null;
        }
    }

    /*
     * setDataType command
     *
     * @param type			0 = Interferogram
     * 						1 = Raw spectrum (realpart only)
     * 						2 = Raw spectrum (real and imaginary)
     * 						3 = Complex spectrum
     * @param mode			0 = Exclusive (transmit only this data type to the transmission)
     * 						1 = Add this type to the transmission
     * 						2 = Remove this type from the transmission
     * @param sigmaMin		-1 = full range
     * @param sigmaMax		-1 = full range
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int setDataType(byte type, byte mode, float sigmaMin, float sigmaMax)
            throws IOException {
        if (client == null) {
            open();
        }

        short phaseFlag = 0;


        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(13);
        cmd.putInt(12);
        cmd.put(type);
        cmd.put(mode);
        cmd.putShort(phaseFlag);
        cmd.putFloat(sigmaMin);
        cmd.putFloat(sigmaMax);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 13 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * acqGetHealthMonitoringStatusDefinition command
     *
     * @return ByteBuffer		contains current status definition
     *
     */
    public ByteBuffer acqGetHealthMonitoringStatusDefinition()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(15);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 15) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        if (len > 0 && state == 0) {
            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();

            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);

            status.position(0);
            return status;
        } else {
            return null;
        }
    }

    /*
     * acqGetBoardCount command
     *
     * @return boardCount	Number of instrument found
     */
    public int acqGetBoardCount()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(16);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(20);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        // Header
        int header = answer.getInt();

        // Command ID
        int cmdId = answer.getInt();

        // State
        answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 16 || len != 4) {
            throw new IOException("Invalid answer");
        }

        return answer.getInt();
    }

    /*
     * acqGetBoardCount command
     *
     * @return boardCount	Number of instrument found
     */
    public int acqGetBoardCount(int udpPortNumber)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(28);
        cmd.putInt(4);
        cmd.putInt(udpPortNumber);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(20);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        // Header
        int header = answer.getInt();

        // Command ID
        int cmdId = answer.getInt();

        // State
        answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 28 || len != 4) {
            throw new IOException("Invalid answer");
        }

        return answer.getInt();
    }

    /**
     * This function is used to get information about acquisition boards.
     *
     * @param boardCount Number of acquisition board present
     * @return boardInfo  	ByteBuffer containing board information of all instruments fount
     *
     */
    public ByteBuffer acqGetBoardInfo(int boardCount)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(17);
        cmd.putInt(4);
        cmd.putInt(boardCount);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 17) {
            throw new IOException("Invalid answer");
        }

        answer.clear();
        answer.limit(len);

        if (len > 0 && state == 0) {
            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();

            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);

            status.position(0);
            return status;
        } else {
            return null;
        }
    }

    /*
     * setResolution command
     *
     * @param resolution index:	0 = 1 cm-1
     * 							1 = 2 cm-1
     * 							2 = 4 cm-1
     * 							3 = 8 cm-1
     * 							4 = 16 cm-1
     * 							5 = 32 cm-1
     * 							6 = 64 cm-1
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int setResolution(short resolution)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(18);
        cmd.putInt(2);
        cmd.putShort(resolution);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 18 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setScanArmSpeed command
     *
     * @param speed index:	0 = xx fringe/sec
     * 							1 = xx fringe/sec
     * 							2 = xx fringe/sec
     * 							3 = xx fringe/sec
     * 							4 = xx fringe/sec
     * 							5 = xx fringe/sec
     * 							6 = xx fringe/sec
     *
     * @return state		0 = success 1 = failure
     */
    public int setScanArmSpeed(short speed)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(19);
        cmd.putInt(2);
        cmd.putShort(speed);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 19 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setSourcePower command
     *
     * @param power			0 = OFF
     * 						1 = ON
     * @param power level	0 =	Default power level
     * 						1 = High power level
     * 						2 = Low power level
     *
     * @return state		0 = success 1 = failure
     */
    public int setSourcePower(short power, short powerLevel)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(20);
        cmd.putInt(4);
        cmd.putShort(power);
        cmd.putShort(powerLevel);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 20 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setGainValues command
     * Idx Gain value
     * 0 = 1    1.0
     * 1 = 2    3.01
     * 2 = 3    9.06
     * 3 = 4    27.13
     * 4 = 5    80.68
     * 5 = 6    237.84
     * 6 = 7    671.41
     * 7 = 8    3600.00
     *
     * @param firstStageGain
     * @param secondStageGain
     *
     * @return state		0 = success 1 = failure
     */
    public int setGainValues(short firstStageGain, short secondStageGain)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(21);
        cmd.putInt(4);
        cmd.putShort(firstStageGain);
        cmd.putShort(secondStageGain);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 21 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setTECSetPoint command
     *
     * @param temperature
     *
     * @return state		0 = success 1 = failure
     */
    public int setTECSetPoint(float temperature)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(22);
        cmd.putInt(4);
        cmd.putFloat(temperature);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 22 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     * setTECPower command
     *
     * @param power 0 = OFF
     *              1 = ON
     * @return state		0 = success 1 = failure
     */

    public int setTECPower(short power)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(23);
        cmd.putInt(2);
        cmd.putShort(power);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 23 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /**
     *
     * @param command
     * @param numberOfMeasurement 1 = Collect mode >1= Kinetic Mode
     * @param numberOfCoadd
     * @param factor
     * @param resultFlag
     * @param delayBefore
     * @param delayBetween
     * @return 0 = success 1 = failure
     * @throws IOException
     */
    public int setCoadditionCommand(short command, int numberOfMeasurement, int numberOfCoadd, int factor, short resultFlag,
                                    float delayBefore, float delayBetween)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(24);
        cmd.putInt(24);
        cmd.putShort(command);
        cmd.putInt(numberOfMeasurement);
        cmd.putInt(numberOfCoadd);
        cmd.putInt(factor);
        cmd.putShort(resultFlag);
        cmd.putFloat(delayBefore);
        cmd.putFloat(delayBetween);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 24 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * stopAcqusition command
     *
     * @return state		0 = sucess
     * 						1 = failure
     */
    public int extendedConfigurationRequest()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(26);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);
        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 26 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * getBoardName command
     *
     * @return ByteBuffer		contains current status of the instrument NGEN
     */
    public ByteBuffer getBoardName()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(27);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 27) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);
            return status;
        } else {
            return null;
        }
    }

    /*
     * setValidationTest command
     *
     * @param  file			path od the Validation Template file
     * @return ByteBuffer	Validation Test Structure
     *
     */
    public ByteBuffer setValidationTest(String file)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(31);
        int length = file.length() * 2;
        cmd.putInt(length);
        putString(cmd, file, length, length);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 31) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);
            return status;
        } else {
            return null;
        }
    }

    /*
     * runValidationTest command
     *
     * @param  id			Validation test ID
     * @return ByteBuffer	Validation Test result buffer
     *
     */
    public int runValidationTest(byte id)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(32);
        cmd.putInt(1);
        cmd.put(id);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 32 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * getValidationResult command
     *
     * @param  id			Validation test ID
     * @return ByteBuffer	Validation result structure
     *
     */
    public ByteBuffer getValidationResult(byte id)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(33);
        cmd.putInt(1);
        cmd.put(id);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 33) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer result = ByteBuffer.allocate(len);
            result.order(ByteOrder.LITTLE_ENDIAN);
            result.put(answer.array(), 0, len);
            result.position(0);
            return result;
        } else {
            return null;
        }
    }

    /*
     * answerValidationDialog command
     *
     * @param  id			Validation test ID
     * @param  answer		0=OK 1=CANCEL
     *
     */
    public int answerValidationDialog(byte id, byte answerValue)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(34);
        cmd.putInt(2);
        cmd.put(id);
        cmd.put(answerValue);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 34 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * abortValidationTest command
     *
     * @param  id			Validation test ID
     *
     */
    public int abortValidationTest(byte id)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(35);
        cmd.putInt(1);
        cmd.put(id);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 35 || len != 0) {
            throw new IOException("Invalid answer");
        }

        return state;
    }

    /*
     * getValidationData command
     *
     * @param  id			Validation test ID
     * @return ByteBuffer	Validation result structure
     *
     */
    public ByteBuffer getValidationData(byte id)
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(36);
        cmd.putInt(1);
        cmd.put(id);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 36) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer data = ByteBuffer.allocate(len);
            data.order(ByteOrder.LITTLE_ENDIAN);
            data.put(answer.array(), 0, len);
            data.position(0);
            return data;
        } else {
            return null;
        }
    }

    /*
     * getDriverVersionNumber command
     *
     * @return ByteBuffer	Validation result structure
     *
     */
    public ByteBuffer getDriverVersion()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(37);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 37) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer version = ByteBuffer.allocate(len);
            version.order(ByteOrder.LITTLE_ENDIAN);
            version.put(answer.array(), 0, len);
            version.position(0);
            return version;
        } else {
            return null;
        }
    }

    /*
     * getControlStatus command
     *
     * @return ByteBuffer		contains control status table used to control the MB3000 series
     */
    public ByteBuffer getControlStatus()
            throws IOException {
        if (client == null) {
            open();
        }

        // Prepare command
        cmd.clear();
        cmd.putInt(COMMAND_HEADER);
        cmd.putInt(38);
        cmd.putInt(0);

        // Send command
        cmd.flip();
        long time0 = System.currentTimeMillis();
        while (cmd.hasRemaining()) {
            client.write(cmd);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }

        // Get answer
        answer.clear();
        answer.limit(16);

        time0 = System.currentTimeMillis();
        while (answer.hasRemaining()) {
            client.read(answer);
            if ((System.currentTimeMillis() - time0) > shortTimeout) {
                throw new IOException("Cmd write timeout");
            }
        }
        answer.flip();

        int header = answer.getInt();
        int cmdId = answer.getInt();
        int state = answer.getInt();
        int len = answer.getInt();
        if (header != RESULT_HEADER || cmdId != 38) {
            throw new IOException("Invalid answer");
        }

        if (len > 0 && state == 0) {
            answer.clear();
            answer.limit(len);

            while (answer.hasRemaining()) {
                client.read(answer);
                if ((System.currentTimeMillis() - time0) > shortTimeout) {
                    throw new IOException("Cmd write timeout");
                }
            }
            answer.flip();
            ByteBuffer status = ByteBuffer.allocate(len);
            status.order(ByteOrder.LITTLE_ENDIAN);
            status.put(answer.array(), 0, len);
            status.position(0);
            return status;
        } else {
            return null;
        }
    }

    /**
     * Converts a <code>String</code> to a 0 terminated<code>UTF-16LE byte []</code>.
     *
     * @param string          the string to be converted.
     * @param stringMaxLength the maximum length of the s-tring.
     * @param resultLength    the length of the reulting string. Even if the string is shorter,
     *                        the resulting string will be 0 padded.
     * @return the resulting <code>byte []</code>.
     */
    private void putString(ByteBuffer buf, String string, int stringMaxLength, int resultLength) {
        if (string.length() >= stringMaxLength) {
            string = string.substring(0, stringMaxLength - 2);
        }
        int length = string.length();
        if (resultLength == 0) {
            resultLength = length * 2 + 2;
        }
        byte[] tmp = new byte[resultLength];
        try {
            if (length != 0) {
                System.arraycopy(string.getBytes("UTF-16LE"), 0, tmp, 0, length * 2);
            }
            buf.put(tmp, 0, resultLength);
        } catch (UnsupportedEncodingException ex) {
            // May not occurs, UTF-16LE is a JDK mandatory implementation
        }
    }

    public class BoardInformation {
        public float structureVersion;
        public byte instrumentType;
        public byte maximumChannel;
        public float acquisitionDriverVersion;

        public void set(float structureVersion, byte instrumentType, byte maximumChannel, float acquisitionDriverVersion) {
            this.structureVersion = structureVersion;
            this.instrumentType = instrumentType;
            this.maximumChannel = maximumChannel;
            this.acquisitionDriverVersion = acquisitionDriverVersion;
        }

    }
}
