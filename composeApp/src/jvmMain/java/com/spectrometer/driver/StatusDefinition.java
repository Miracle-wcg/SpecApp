/* StatusDefinition.java
 *---------------------- Copyright (c) ABB Bomem inc, 2004 -------------------
 *                               Source code
 * This software is the property of ABB and should be considered and treated
 * as proprietary information.  Refer to the "Source Code License Agreement"
 *----------------------------------------------------------------------------
 *
 *  Revision history
 * $Log:   M:/VM8/VMPROJECT/GENE/archives/AcquisitionDriverClient/JavaCode/Src/com/bomem/gene/driverClient/StatusDefinition.java-arc  $
 *
 *    Rev 1.3   Nov 24 2008 08:39:30   cmbourqu
 * Updating!
 *
 *    Rev 1.2   Apr 14 2008 12:00:20   cmbourqu
 * Updating with struture version 4.0.
 *
 *    Rev 1.1   Apr 01 2008 15:46:40   cmbourqu
 * New structure:
 *
 * 	Name -> 128 characters
 * 	Description become Units
 * 	Add propertyType.
 *
 *    Rev 1.0   Oct 18 2007 09:07:56   cmbourqu
 * Initial revision.
 *
 *    Rev 1.1   Mar 14 2007 09:23:02   malefebv
 * Use of ISpectrumFactory in sqcSpectral operations
 *
 *    Rev 1.0   Feb 05 2007 10:02:02   cmbourqu
 * Initial revision.
 *
 *    Rev 1.3   Jan 18 2007 16:00:18   cmbourqu
 * Implement Hidden field.
 *
 *    Rev 1.2   Dec 12 2006 08:18:38   cmbourqu
 * Remove debug stuff
 *
 *    Rev 1.1   Dec 07 2006 21:12:12   cmbourqu
 * Remove debugging!
 *
 *    Rev 1.0   Dec 07 2006 11:46:48   cmbourqu
 * Initial revision.
 *
 *    Rev 1.0   Dec 07 2006 11:37:36   cmbourqu
 * Initial revision.
 *
 */

package com.spectrometer.driver;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class StatusDefinition {
    public static final int NAME_MAX_SIZE = 128;    // 64 Unicode characters
    public static final int UNIT_MAX_SIZE = 64;    // 32 Unicode characters

    public static final byte HEADER_TYPE = 0;
    public static final byte SUBFILE_TYPE = 1;

    public static final byte STRING_MAX_SIZE = 64;
    public static final byte TYPE_BYTE = 0;
    public static final byte TYPE_SHORT = 1;
    public static final byte TYPE_INT = 2;
    public static final byte TYPE_LONG = 3;
    public static final byte TYPE_FLOAT = 4;
    public static final byte TYPE_DOUBLE = 5;
    public static final byte TYPE_STRING = 6;

    public static final byte SHOW = 0;
    public static final byte HIDE = 1;

    public static final int SPECTRUM_TYPE_MID = 0;
    public static final int SPECTRUM_TYPE_NIR = 1;

    /**
     * (64 + 64 + 1 + 1 + 4 + 4 + 1)
     */
    private static final int STATUS_LENGTH = 138;
    private static final int DEFINITION_BUFFER_SIZE = (NAME_MAX_SIZE + UNIT_MAX_SIZE + 11);

    public float structureVersion = (float) 2.0;
    public int size = 0;
    public Status[] status;


    /**
     * Default Constructor
     *
     */
    public StatusDefinition(int size, float structureVersion) {
        this.size = size;
        this.structureVersion = structureVersion;

        status = new Status[size];
        for (int i = 0; i < size; i++) {
            status[i] = new Status();
        }
    }

    public ByteBuffer getBuffer() {
        if (size > 0) {
            ByteBuffer buffer = ByteBuffer.allocate((DEFINITION_BUFFER_SIZE * size) + 8);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putFloat(structureVersion);
            buffer.putInt(size);
            for (int i = 0; i < size; i++) {
                putString(buffer, status[i].name, status[i].name.length() + 4, NAME_MAX_SIZE);
                putString(buffer, status[i].unit, status[i].unit.length() + 4, UNIT_MAX_SIZE);
                buffer.put(status[i].type);
                buffer.put(status[i].propertyType);
                buffer.putInt(status[i].length);
                buffer.putInt(status[i].index);
                buffer.put(status[i].hidden);
            }

            buffer.position(0);
            return buffer;
        }
        return null;
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
    public void putString(ByteBuffer buf, String string, int stringMaxLength, int resultLength) {
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

    /**
     * Extracts a 0 terminated<code>UTF-16LE byte []</code> string from <code>ByteBuffer</code>.
     *
     * @param buf      the <code>ByteBuffer</code> from which to perform the extraction.
     * @param nbrBytes the number of bytes to be extracted
     * @return the resulting Unicode <code>String</code>.
     */
    public String getString(ByteBuffer buf, int nbrBytes) {
        byte[] tmp = new byte[nbrBytes];
        buf.get(tmp, 0, nbrBytes);
        int length = 0;
        for (; length < nbrBytes; length += 2) {
            if (tmp[length] == 0 && tmp[length + 1] == 0) {
                break;
            }
        }
        try {
            return new String(tmp, 0, length, "UTF-16LE");
        } catch (UnsupportedEncodingException ex) {
            // May not occurs, UTF-16LE is a JDK mandatory implementation
            return "";
        }
    }

    /**
     * Item class
     */
    public class Status {
        public String name;            // 128 bytes
        public String unit;            // 64 bytes
        public byte type;            // Data type
        public byte propertyType;    // Header or subfile property
        public int length;            // 0 if not an array
        public int index;            // Index in status buffer
        public byte hidden;            // Showable flag

        private Status() {
        }

        public void init(String name, String unit, byte type, byte propertyType, int length, int index, byte hidden) {
            this.name = name;
            this.unit = unit;
            this.type = type;
            this.propertyType = propertyType;
            this.length = length;
            this.index = index;
            this.hidden = hidden;
        }
    }

}
