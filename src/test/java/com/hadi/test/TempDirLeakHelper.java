package com.hadi.test;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;

/**
 * Test fixture run in a child JVM by {@code ProjectFilesTest}: it persists a {@link ProjectFiles}
 * temp dir and then exits WITHOUT calling {@link ProjectFiles#close()}, so the parent test can assert
 * that clarpse's JVM-shutdown hook deleted the otherwise-leaked temp dir. The persisted dir path is
 * printed prefixed with {@code TEMPDIR:} for the parent to read back.
 */
public final class TempDirLeakHelper {

    private TempDirLeakHelper() {
    }

    public static void main(final String[] args) {
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/leak/A.java", "package leak; class A {}"));
        System.out.println("TEMPDIR:" + projectFiles.projectDir());
        // Intentionally NOT closed: the shutdown hook must clean the temp dir on JVM exit.
    }
}
