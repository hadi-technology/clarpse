package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ClarpseTestUtil {

    public static String unzipArchive(File archivedFile) throws IOException {
        Path tmpPath = Paths.get(FileUtils.getTempDirectory().getAbsolutePath(),
                           UUID.randomUUID().toString());
        String tmpdir = Files.createDirectories(tmpPath).toFile().getAbsolutePath();
        tmpPath.toFile().deleteOnExit();
        try (ZipFile zipFile = new ZipFile(archivedFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(tmpdir, entry.getName());
                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        IOUtils.copy(in, out);
                    }
                }
            }
        }
        return tmpdir;
    }

    public static OOPSourceCodeModel sourceCodeModel(String testResourceZip, Lang language) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles(ClarpseTestUtil.class.getResourceAsStream(testResourceZip));
        return new ClarpseProject(projectFiles, language).result().model();
    }
}