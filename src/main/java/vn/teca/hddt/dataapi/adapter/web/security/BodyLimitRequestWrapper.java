package vn.teca.hddt.dataapi.adapter.web.security;

import java.io.IOException;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/** Chặn body vượt giới hạn kể cả khi không có Content-Length (chunked). */
public class BodyLimitRequestWrapper extends HttpServletRequestWrapper {
    private final long maxBytes;

    public BodyLimitRequestWrapper(HttpServletRequest request, long maxBytes) {
        super(request);
        this.maxBytes = maxBytes;
    }

    /** Ném khi body vượt giới hạn — được ánh xạ thành HTTP 413. */
    public static class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException() {
            super("Body vượt giới hạn");
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        ServletInputStream in = super.getInputStream();
        return new ServletInputStream() {
            private long read;

            @Override
            public int read() throws IOException {
                int b = in.read();
                if (b >= 0 && ++read > maxBytes) {
                    throw new PayloadTooLargeException();
                }
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                int n = in.read(buf, off, len);
                if (n > 0) {
                    read += n;
                    if (read > maxBytes) {
                        throw new PayloadTooLargeException();
                    }
                }
                return n;
            }

            @Override
            public boolean isFinished() {
                return in.isFinished();
            }

            @Override
            public boolean isReady() {
                return in.isReady();
            }

            @Override
            public void setReadListener(ReadListener listener) {
                in.setReadListener(listener);
            }
        };
    }
}
