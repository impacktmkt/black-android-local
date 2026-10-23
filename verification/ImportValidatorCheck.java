package com.racas.black;

import java.lang.reflect.Method;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

// Exact upstream importer validator, exercised on host JVM; not an Android/device test.
public class ImportValidatorCheck {
    public static void main(String[] args) throws Exception {
        Method validate = GameImporter.class.getDeclaredMethod("isXboxImage", FileChannel.class);
        validate.setAccessible(true);
        int checks = 0;
        Path image = Files.createTempFile("black-validator-", ".iso");
        try {
            for (long partition : new long[]{0, 0x18300000L, 0xFD90000L, 0x2080000L}) {
                try (RandomAccessFile file = new RandomAccessFile(image.toFile(), "rw")) {
                    file.setLength(0);
                    file.seek(partition + 32 * 2048);
                    file.write("MICROSOFT*XBOX*MEDIA".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    file.getChannel().position(7);
                    if (!Boolean.TRUE.equals(validate.invoke(null, file.getChannel())))
                        throw new AssertionError("Rejected partition " + partition);
                    if (file.getChannel().position() != 7) throw new AssertionError("Changed copy position");
                    checks += 2;
                }
            }
            for (int length : new int[]{0, 20, 65540}) {
                try (RandomAccessFile file = new RandomAccessFile(image.toFile(), "rw")) {
                    file.setLength(0);
                    file.setLength(length);
                    if (!Boolean.FALSE.equals(validate.invoke(null, file.getChannel())))
                        throw new AssertionError("Accepted invalid/truncated image");
                    checks++;
                }
            }
            try (FileChannel supplied = FileChannel.open(Path.of(args[0]))) {
                if (!Boolean.TRUE.equals(validate.invoke(null, supplied)))
                    throw new AssertionError("Supplied ISO failed upstream validator");
                checks++;
            }
            System.out.println("PASS: " + checks + " importer validation assertions (host JVM only)");
        } finally {
            Files.deleteIfExists(image);
        }
    }
}
