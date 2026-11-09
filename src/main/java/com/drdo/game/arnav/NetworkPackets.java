package com.drdo.game.arnav;

import org.apache.coyote.http11.filters.SavedRequestInputFilter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class NetworkPackets {

    private static boolean[] convertIntToBoolean(int val, int len){
        boolean[] array = new boolean[len];
        for (int i = 0; i < len; i++){
            array[i] = (val & (1 << i)) != 0;
        }
        return array;
    }

    public static void main(String[] args) {
        boolean[] x = convertIntToBoolean(11, 5);
        System.out.println(Arrays.toString(x));
    }

    public void someMethod(){
        short comCode = (short) Integer.parseInt("001100", 2);
        byte msgBodyLen = 18;
        String word1Str = "01111111_00000000";
        short word1 = (short) Integer.parseUnsignedInt(word1Str, 2); // 32512

        String word2Str = "1111111111_0_0100_0";
        short word2 = (short) Integer.parseUnsignedInt(word1Str, 2); // -56

        String word3_4Str = "1_1111111111111111111111_000000000";
        short word3_4 = (short) Integer.parseUnsignedInt(word3_4Str, 2); // -512

        String word5_6Str = "1_1111111111111111111111_000000000";
        short word5_6 = (short) Integer.parseUnsignedInt(word5_6Str, 2); // -512

        String word7Str = "11101_110111_00000";
        short word7_p = (short) Integer.parseUnsignedInt(word7Str, 2); // -4384

        String word8Str = "110111_000_00000_00";
        short word8_p = (short) Integer.parseUnsignedInt(word8Str, 2); // -9216

        String word9Str = "0000000000_001011";
        short word9 = (short) Integer.parseUnsignedInt(word9Str, 2); // 19

        ByteBuffer bb = ByteBuffer.allocate(23);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) comCode); // 12
        bb.put(msgBodyLen); // 18
        bb.putShort(word1);
        bb.putShort(word2);
        bb.putShort(word3_4);
        bb.putShort(word5_6);
        bb.putShort(word7_p);
        bb.putShort(word8_p);
        bb.putShort(word9);
        bb.putShort((short) calcCheckSumCRC16Bit(bb.array()));
    }

    int calcCheckSumCRC16Bit(byte[] data){

        int currentCRCVal = 0;
        int presetVal = 0;
        for (int i = 0; i < data.length; i++) {
            currentCRCVal ^= data[i] & 0xFF;
            for (int j = 0; j < 8; j++) {
                if ((currentCRCVal & 1) != 0){
                    currentCRCVal = ((currentCRCVal >>> 1) ^ 0x8408);
                }
                else {
                    currentCRCVal = (currentCRCVal >>> 1);
                }
            }
        }
        return currentCRCVal & 0xFFFF;

    }

    public byte[] message39() {
        System.out.println("Starting message39 block .........");
        comcode = Byte.parseByte("", 2);

        // Word 1.
        // Net Unit Address
        byte netUnitAddr = 127;
        // Reserved
        boolean[] rsv1 = convertIntToBoolean(0, 8);
        String NetUnitAddr = convertBooleanArrToBitString(netUnitAddr);
        String Rsv1 = convertBooleanArrToBitString(rsv1);
        String word1Str = NetUnitAddr + Rsv1;
        short word1 = (short) Integer.parseUnsignedInt(word1Str, 2);

        // Word 2.
        // Detection Time - Day
        boolean[] dtDat = convertIntToBoolean(31, 5);
        // Detection Time - Month
        boolean[] dtMonth = convertIntToBoolean(12, 4);
        // Detection Time - Year
        boolean[] dtYear = convertIntToBoolean(31, 5);
        // Reserved
        boolean[] rsv2 = convertIntToBoolean(0, 2);
        String DtDat = convertBoolealArrToBitString(dtDat);
        String DtMonth = convertBoolealArrToBitString(dtMonth);
        String DtYear = convertBoolealArrToBitString(dtYear);
        String Rsv2 = convertBoolealArrToBitString(rsv2);
        String word2Str = DtDat + DtMonth + DtYear + Rsv2;
        short word2 = (short) Integer.parseUnsignedInt(word2Str, 2);

        // Word 3.
        // Detection Time - Hours
        boolean[] dtHrs = convertIntToBoolean(23, 5);
        // Detection Time - Minutes
        boolean[] dtMin = convertIntToBoolean(59, 6);
        // Reserved
        boolean[] rsv3 = convertIntToBoolean(0, 5);
        String DtHrs = convertBooleanArrToBitString(dtHrs);
        String DtMin = convertBooleanArrToBitString(dtMin);
        String Rsv3 = convertBooleanArrToBitString(rsv3);
        String word3Str = DtHrs + DtMin + Rsv3;
        short word3 = (short) Integer.parseUnsignedInt(word3Str, 2);

        // Word 4.
        // Detection Source
        boolean[] dtSrc4 = convertIntToBoolean(0, 16);
        String word4Str = convertBooleanArrToBitString(dtSrc4);
        short word4 = (short) Integer.parseUnsignedInt(word4Str, 2);

        // Word 5.
        // Detection Source
        boolean[] dtSrc5 = convertIntToBoolean(0, 16);
        String word5Str = convertBooleanArrToBitString(dtSrc5);
        short word5 = (short) Integer.parseUnsignedInt(word5Str, 2);

        // Word 6.
        // Detection Source
        boolean[] dtSrc6 = convertIntToBoolean(0, 16);
        String word6Str = convertBooleanArrToBitString(dtSrc6);
        short word6 = (short) Integer.parseUnsignedInt(word6Str, 2);

        // Word 7.
        // Detection Source
        boolean[] dtSrc7 = convertIntToBoolean(0, 16);
        String word7Str = convertBooleanArrToBitString(dtSrc7);
        short word7 = (short) Integer.parseUnsignedInt(word7Str, 2);

        // Word 8.
        // Detection Source
        boolean[] dtSrc8 = convertIntToBoolean(0, 16);
        String word8Str = convertBooleanArrToBitString(dtSrc8);
        short word8 = (short) Integer.parseUnsignedInt(word8Str, 2);

        // Word 9.
        // Detection Source
        boolean[] dtSrc9 = convertIntToBoolean(0, 16);
        String word9Str = convertBooleanArrToBitString(dtSrc9);
        short word9 = (short) Integer.parseUnsignedInt(word9Str, 2);

        // Word 10.
        // Detection Source
        boolean[] dtSrc10 = convertIntToBoolean(0, 16);
        String word10Str = convertBooleanArrToBitString(dtSrc10);
        short word10 = (short) Integer.parseUnsignedInt(word10Str, 2);

        // Word 11.
        // Detection Source
        boolean[] dtSrc11 = convertIntToBoolean(0, 16);
        String word11Str = convertBooleanArrToBitString(dtSrc11);
        short word11 = (short) Integer.parseUnsignedInt(word11Str, 2);

        // Word 36.
        // Composition of Formation
        boolean[] compOfFormation = convertIntToBoolean(63, 6);
        // NO of Targets in Group
        boolean[] noOfTgtInGrp = convertIntToBoolean(255, 8);
        // Reserved
        boolean[] rsv36 = convertIntToBoolean(0, 2);
        String CompOfFormation = convertBoooleanArrToBitString(compOfFormation);
        String NoOfTgtInGrp = convertBoooleanArrToBitString(noOfTgtInGrp);
        String Rsv36 = convertBoooleanArrToBitString(rsv36);
        String word36Str = CompOfFormation + NoOfTgtInGrp + Rsv36;
        short word36 = (short) Integer.parseUnsignedInt(word36Str, 2);


        // Word 39.
        // Target Longitude Minutes
        boolean[] tgtLongMin = convertIntToBoolean(59, 6);
        // Target Longitude Seconds
        boolean[] tgtLongSec = convertIntToBoolean(59, 6);
        // Reserved
        boolean[] rsv39 = convertIntToBoolean(0, 4);
        String TgtLongMin = convertBooleanArrToBitString(tgtLongMin);
        String TgtLongSec = convertBooleanArrToBitString(tgtLongSec);
        String Rsv39 = convertBooleanArrToBitString(rsv39);
        String word39Str = TgtLongMin + TgtLongSec + Rsv39;
        short word39 = (short) Integer.parseUnsignedInt(word39Str, 2);

        // Word 40.
        // Target Course, MSB=180 deg
        boolean[] tgtCourse = convertIntToBoolean(180, 16);
        String TgtCourse = convertBooleanArrToBitString(tgtCourse);
        short word40 = (short) Integer.parseUnsignedInt(TgtCourse, 2);

        // Word 41.
        // Target Speed, MSB=180 deg
        boolean[] tgtSpeed = convertIntToBoolean(180, 16);
        String TgtSpeed = convertBooleanArrToBitString(tgtSpeed);
        short word41 = (short) Integer.parseUnsignedInt(TgtSpeed, 2);

        // Word 42.
        // Target height/depth, MSB=180 deg
        boolean[] tgtHgtDep = convertIntToBoolean(180, 16);
        String TgtHgtDep = convertBooleanArrToBitString(tgtHgtDep);
        short word42 = (short) Integer.parseUnsignedInt(TgtHgtDep, 2);

        // Word 43.
        // Radius of Full Group
        boolean[] radOfFullGrp = convertIntToBoolean(250, 8);
        // Reserved
        boolean[] rsv43 = convertIntToBoolean(0, 8);
        String RadOfFullGrp = convertBooleanArrToBitString(radOfFullGrp);
        String Rsv43 = convertBooleanArrToBitString(rsv43);
        String word43Str = RadOfFullGrp + Rsv43;
        short word43 = (short) Integer.parseUnsignedInt(word43Str, 2);


    }




}
