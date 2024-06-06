package org.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class MemMappedFileWriter {

    public static void main(String[] args) {
        File file = new File("/home/paulograbin/Desktop/book.txt");

        try {
            boolean delete = file.delete();
            if (delete) {
                System.out.println("Deleted " + file.getAbsolutePath());
                boolean newFile = file.createNewFile();
                if (newFile) {
                    System.out.println("Created new file " + file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            System.err.println("Não deu");
            System.exit(1);
        }

        if (!file.exists()) {
            System.exit(1);
        }

        try (var rar = new RandomAccessFile(file, "rw")) {
            byte[] bytes = "aaaabbbcccc".getBytes(StandardCharsets.UTF_8);

            final var mappedFile = rar.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 1024, Arena.global());
            System.out.println("Base address " + mappedFile.address());

            Byte b = (byte) 4565456;

            mappedFile.setAtIndex(ValueLayout.JAVA_BYTE, 0L, b);
//            mappedFile.setAtIndex(ValueLayout.JAVA_BYTE, 1L, (byte) 10);
//            mappedFile.setAtIndex(ValueLayout.JAVA_BYTE, 1L, true);


//            final var mappedByteBuffer = rar.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, bytes.length);

//            for (byte aByte : bytes) {
//                mappedByteBuffer.put(aByte);
//            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
