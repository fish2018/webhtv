package com.fongmi.android.tv.node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 从 zip 里单独取一个条目——只为 libnode.so，避免把三个架构全解出来。 */
final class NodeZip {

    private NodeZip() {
    }

    static boolean extract(File zip, String entryName, File target) throws IOException {
        try (ZipFile file = new ZipFile(zip)) {
            ZipEntry entry = file.getEntry(entryName);
            if (entry == null) return false;
            if (target.getParentFile() != null) target.getParentFile().mkdirs();
            try (InputStream in = file.getInputStream(entry); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            target.setReadable(true, false);
            return target.length() > 0;
        }
    }
}
