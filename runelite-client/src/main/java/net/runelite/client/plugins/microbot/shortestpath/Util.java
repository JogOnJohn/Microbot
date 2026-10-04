package net.runelite.client.plugins.microbot.shortestpath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Util {
    public static int[] concatenate(int[][] arrays) {
        int size = 0;
        for (int[] array : arrays) size += array == null ? 0 : array.length;
        if (size == 0) return null;
        int[] result = new int[size];
        int offset = 0;
        for (int[] array : arrays) {
            if (array != null) {
                System.arraycopy(array, 0, result, offset, array.length);
                offset += array.length;
            }
        }
        return result;
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        while (true) {
            int read = in.read(buffer, 0, buffer.length);

            if (read == -1) {
                return result.toByteArray();
            }

            result.write(buffer, 0, read);
        }
    }
}
