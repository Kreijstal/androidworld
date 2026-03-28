package com.example.build;

import java.io.*;
import java.util.zip.*;

/**
 * Minimal APK builder. Creates an APK (ZIP) containing:
 * - AndroidManifest.xml (binary)
 * - classes.dex
 */
public class ApkBuilder {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: ApkBuilder <manifest.bin> <classes.dex> <output.apk>");
            System.exit(1);
        }

        String manifestPath = args[0];
        String dexPath = args[1];
        String outputPath = args[2];

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputPath))) {
            // AndroidManifest.xml must be stored (not deflated) for some tools
            addEntry(zos, "AndroidManifest.xml", new File(manifestPath), ZipEntry.STORED);
            addEntry(zos, "classes.dex", new File(dexPath), ZipEntry.STORED);
        }

        System.out.println("Built APK: " + outputPath);
    }

    private static void addEntry(ZipOutputStream zos, String name, File file, int method) throws IOException {
        byte[] data = readFile(file);

        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(method);
        if (method == ZipEntry.STORED) {
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setCrc(crc.getValue());
        }
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readFile(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = fis.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        return data;
    }
}
