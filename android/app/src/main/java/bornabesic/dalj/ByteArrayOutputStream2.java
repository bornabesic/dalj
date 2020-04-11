package bornabesic.dalj;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class ByteArrayOutputStream2 extends OutputStream {

    public byte[] bytes = null;
    public int length = 0;
    private int i = 0;

    public ByteArrayOutputStream2(int size) {
        super();
        bytes = new byte[size];
        length = bytes.length;
    }

    @Override
    public void write(int b) throws IOException {
        bytes[i] = (byte) b;
        i++;
    }

    public void reset() {
        i = 0;
        Arrays.fill(bytes, (byte) 0);
    }

}
