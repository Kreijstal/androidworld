package com.example.build;

import java.io.*;
import java.util.*;

/**
 * Minimal Android binary XML encoder.
 * Encodes a simple AndroidManifest.xml into the binary format Android expects.
 * Only supports what's needed for a basic Hello World app.
 *
 * Resource IDs from android.R$attr (API 28):
 *   name             = 0x01010003
 *   label            = 0x01010001
 *   exported         = 0x01010010
 *   minSdkVersion    = 0x0101020c
 *   targetSdkVersion = 0x01010270
 *   versionCode      = 0x0101021b
 *   versionName      = 0x0101021c
 */
public class BinaryXmlEncoder {

    private static final short RES_XML_TYPE = 0x0003;
    private static final short RES_STRING_POOL_TYPE = 0x0001;
    private static final short RES_XML_RESOURCE_MAP_TYPE = 0x0180;
    private static final short RES_XML_START_NAMESPACE_TYPE = 0x0100;
    private static final short RES_XML_END_NAMESPACE_TYPE = 0x0101;
    private static final short RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final short RES_XML_END_ELEMENT_TYPE = 0x0103;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final List<String> stringPool = new ArrayList<>();
    private final List<Integer> resourceIds = new ArrayList<>();
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    private int getOrAddString(String s) {
        int idx = stringPool.indexOf(s);
        if (idx < 0) {
            idx = stringPool.size();
            stringPool.add(s);
        }
        return idx;
    }

    public byte[] encode(String packageName, String appLabel, String activityName,
                         int minSdk, int targetSdk, int versionCode, String versionName) throws IOException {
        // Resource-mapped attribute strings MUST come first in string pool.
        // The resource map maps stringPool[i] -> resourceIds[i] for i < resourceIds.size()
        // These must be sorted by resource ID for Android's binary search.
        int idxLabel = getOrAddString("label");
        resourceIds.add(0x01010001);

        int idxName = getOrAddString("name");
        resourceIds.add(0x01010003);

        int idxExported = getOrAddString("exported");
        resourceIds.add(0x01010010);

        int idxMinSdk = getOrAddString("minSdkVersion");
        resourceIds.add(0x0101020c);

        int idxVersionCode = getOrAddString("versionCode");
        resourceIds.add(0x0101021b);

        int idxVersionName = getOrAddString("versionName");
        resourceIds.add(0x0101021c);

        int idxTargetSdk = getOrAddString("targetSdkVersion");
        resourceIds.add(0x01010270);

        // Non-mapped strings
        int idxAndroidNs = getOrAddString(ANDROID_NS);
        int idxAndroid = getOrAddString("android");
        int idxPackageStr = getOrAddString("package");
        int idxManifest = getOrAddString("manifest");
        int idxApplication = getOrAddString("application");
        int idxActivity = getOrAddString("activity");
        int idxIntentFilter = getOrAddString("intent-filter");
        int idxAction = getOrAddString("action");
        int idxCategory = getOrAddString("category");
        int idxUsesSdk = getOrAddString("uses-sdk");
        int idxPackageVal = getOrAddString(packageName);
        int idxAppLabelVal = getOrAddString(appLabel);
        int idxActivityVal = getOrAddString(activityName);
        int idxMainAction = getOrAddString("android.intent.action.MAIN");
        int idxLauncherCat = getOrAddString("android.intent.category.LAUNCHER");
        int idxVersionNameVal = getOrAddString(versionName);

        int line = 1;

        // Start namespace
        writeStartNamespace(line, idxAndroid, idxAndroidNs);

        // <manifest package="..." android:versionCode="..." android:versionName="...">
        writeStartElement(line, -1, idxManifest, new int[][]{
            {-1, idxPackageStr, idxPackageVal, 0x03, -1},                    // package (no namespace)
            {idxAndroidNs, idxVersionCode, -1, 0x10, versionCode},           // TYPE_INT_DEC
            {idxAndroidNs, idxVersionName, idxVersionNameVal, 0x03, -1},     // TYPE_STRING
        });

        // <uses-sdk android:minSdkVersion="..." android:targetSdkVersion="..."/>
        line++;
        writeStartElement(line, -1, idxUsesSdk, new int[][]{
            {idxAndroidNs, idxMinSdk, -1, 0x10, minSdk},
            {idxAndroidNs, idxTargetSdk, -1, 0x10, targetSdk},
        });
        writeEndElement(line, -1, idxUsesSdk);

        // <application android:label="...">
        line++;
        writeStartElement(line, -1, idxApplication, new int[][]{
            {idxAndroidNs, idxLabel, idxAppLabelVal, 0x03, -1},
        });

        // <activity android:name="..." android:exported="true">
        line++;
        writeStartElement(line, -1, idxActivity, new int[][]{
            {idxAndroidNs, idxName, idxActivityVal, 0x03, -1},
            {idxAndroidNs, idxExported, -1, 0x12, 0xFFFFFFFF},  // TYPE_INT_BOOLEAN, true
        });

        // <intent-filter>
        line++;
        writeStartElement(line, -1, idxIntentFilter, new int[][]{});

        // <action android:name="android.intent.action.MAIN"/>
        line++;
        writeStartElement(line, -1, idxAction, new int[][]{
            {idxAndroidNs, idxName, idxMainAction, 0x03, -1},
        });
        writeEndElement(line, -1, idxAction);

        // <category android:name="android.intent.category.LAUNCHER"/>
        line++;
        writeStartElement(line, -1, idxCategory, new int[][]{
            {idxAndroidNs, idxName, idxLauncherCat, 0x03, -1},
        });
        writeEndElement(line, -1, idxCategory);

        writeEndElement(line, -1, idxIntentFilter);
        writeEndElement(line, -1, idxActivity);
        writeEndElement(line, -1, idxApplication);
        writeEndElement(line, -1, idxManifest);

        writeEndNamespace(line, idxAndroid, idxAndroidNs);

        // Assemble full document
        byte[] stringPoolBytes = encodeStringPool();
        byte[] resourceMapBytes = encodeResourceMap();
        byte[] bodyBytes = body.toByteArray();

        int totalSize = 8 + stringPoolBytes.length + resourceMapBytes.length + bodyBytes.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShort(out, RES_XML_TYPE);
        writeShort(out, (short) 8);
        writeInt(out, totalSize);
        out.write(stringPoolBytes);
        out.write(resourceMapBytes);
        out.write(bodyBytes);

        return out.toByteArray();
    }

    private byte[] encodeStringPool() throws IOException {
        int count = stringPool.size();

        // Encode strings as UTF-16LE
        ByteArrayOutputStream stringData = new ByteArrayOutputStream();
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = stringData.size();
            String s = stringPool.get(i);
            writeShort(stringData, (short) s.length());
            for (char c : s.toCharArray()) {
                writeShort(stringData, (short) c);
            }
            writeShort(stringData, (short) 0);
        }
        // Pad string data to 4-byte alignment
        while (stringData.size() % 4 != 0) {
            stringData.write(0);
        }

        int headerSize = 28;
        int offsetsSize = count * 4;
        int stringsStart = headerSize + offsetsSize;
        int totalSize = stringsStart + stringData.size();

        ByteArrayOutputStream pool = new ByteArrayOutputStream();
        writeShort(pool, RES_STRING_POOL_TYPE);
        writeShort(pool, (short) headerSize);
        writeInt(pool, totalSize);
        writeInt(pool, count);        // stringCount
        writeInt(pool, 0);            // styleCount
        writeInt(pool, 0);            // flags (0 = UTF-16)
        writeInt(pool, stringsStart); // stringsStart
        writeInt(pool, 0);            // stylesStart

        for (int offset : offsets) {
            writeInt(pool, offset);
        }
        pool.write(stringData.toByteArray());

        return pool.toByteArray();
    }

    private byte[] encodeResourceMap() throws IOException {
        ByteArrayOutputStream map = new ByteArrayOutputStream();
        int totalSize = 8 + resourceIds.size() * 4;

        writeShort(map, RES_XML_RESOURCE_MAP_TYPE);
        writeShort(map, (short) 8);
        writeInt(map, totalSize);

        for (int id : resourceIds) {
            writeInt(map, id);
        }
        return map.toByteArray();
    }

    private void writeStartNamespace(int lineNumber, int prefix, int uri) throws IOException {
        writeShort(body, RES_XML_START_NAMESPACE_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, 24);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, prefix);
        writeInt(body, uri);
    }

    private void writeEndNamespace(int lineNumber, int prefix, int uri) throws IOException {
        writeShort(body, RES_XML_END_NAMESPACE_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, 24);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, prefix);
        writeInt(body, uri);
    }

    private void writeStartElement(int lineNumber, int nsIdx, int nameIdx, int[][] attrs) throws IOException {
        int attrCount = attrs.length;
        int chunkSize = 36 + attrCount * 20;

        writeShort(body, RES_XML_START_ELEMENT_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, chunkSize);
        writeInt(body, lineNumber);
        writeInt(body, -1);

        writeInt(body, nsIdx);
        writeInt(body, nameIdx);
        writeShort(body, (short) 0x14);  // attributeStart
        writeShort(body, (short) 0x14);  // attributeSize
        writeShort(body, (short) attrCount);
        writeShort(body, (short) 0);     // idIndex
        writeShort(body, (short) 0);     // classIndex
        writeShort(body, (short) 0);     // styleIndex

        for (int[] attr : attrs) {
            int aNs = attr[0];
            int aName = attr[1];
            int aRawValue = attr[2];
            int aDataType = attr[3];
            int aData = attr[4];

            writeInt(body, aNs);
            writeInt(body, aName);
            writeInt(body, aRawValue);
            writeShort(body, (short) 8);
            body.write(0);
            body.write(aDataType);
            if (aData == -1 && aDataType == 0x03) {
                writeInt(body, aRawValue);
            } else {
                writeInt(body, aData);
            }
        }
    }

    private void writeEndElement(int lineNumber, int nsIdx, int nameIdx) throws IOException {
        writeShort(body, RES_XML_END_ELEMENT_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, 24);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, nsIdx);
        writeInt(body, nameIdx);
    }

    private static void writeShort(OutputStream out, short v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: BinaryXmlEncoder <package> <label> <activity> <output> [minSdk] [targetSdk] [versionCode] [versionName]");
            System.exit(1);
        }
        String packageName = args[0];
        String label = args[1];
        String activity = args[2];
        String outputFile = args[3];
        int minSdk = args.length > 4 ? Integer.parseInt(args[4]) : 21;
        int targetSdk = args.length > 5 ? Integer.parseInt(args[5]) : 28;
        int versionCode = args.length > 6 ? Integer.parseInt(args[6]) : 1;
        String versionName = args.length > 7 ? args[7] : "1.0";

        BinaryXmlEncoder encoder = new BinaryXmlEncoder();
        byte[] data = encoder.encode(packageName, label, activity, minSdk, targetSdk, versionCode, versionName);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(data);
        }
        System.out.println("Wrote binary manifest: " + outputFile + " (" + data.length + " bytes)");
    }
}
