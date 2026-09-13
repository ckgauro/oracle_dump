package com.oracle.checksum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "checksum")
public class ChecksumProperties {

    /** Digest algorithm used to checksum dump files, e.g. SHA-256. */
    private String algorithm = "SHA-256";

    private final Executor executor = new Executor();
    private final Scan scan = new Scan();

    @Getter
    @Setter
    public static class Executor {
        /** "virtual" (default) or "fixed". */
        private Type type = Type.VIRTUAL;
        /** Pool size used only when type is FIXED. */
        private int fixedPoolSize = Runtime.getRuntime().availableProcessors();

        public enum Type {
            VIRTUAL, FIXED
        }
    }

    @Getter
    @Setter
    public static class Scan {
        /** Cron expression driving ChecksumScanScheduler. */
        private String cron = "0/30 * * * * *";
    }
}
