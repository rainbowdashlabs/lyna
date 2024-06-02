package de.chojo.lyna.util;

import java.io.ByteArrayInputStream;

public class OpenByteArrayInputStream extends ByteArrayInputStream {

    public OpenByteArrayInputStream(byte[] buf) {
        super(buf);
    }

    public OpenByteArrayInputStream(byte[] buf, int offset, int length) {
        super(buf, offset, length);
    }

    public int pos() {
        return super.pos;
    }
}
