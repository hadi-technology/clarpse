package com.hadi.clarpse;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a resolved relative directory path.
 */
public class ResolvedRelativePath {

    private final String unresolvedRelativePath;
    private final String absPath;

    /**
     * @param absoluteDir
     *            An absolute directory path.
     * @param relativeFilePath
     *            A relative file path from the absolute dir for which another absolute Path is
     *            required.
     */
    public ResolvedRelativePath(String absoluteDir, String relativeFilePath) throws Exception {
        this.absPath = absoluteDir;
        this.unresolvedRelativePath = relativeFilePath;
        if (relativeFilePath.startsWith("/")) {
            throw new Exception("The given relative file path cannot be an absolute path!");
        }
    }

    private String preprocessedAbsPath(String path) throws Exception {
        // pre-processing...
        path = new TrimmedString(path, "/").value();
        return commonPathPreprocess(path);
    }

    private String commonPathPreprocess(String path) {
        String processedPath = path;
        if (processedPath.contains("/") && processedPath.substring(processedPath.lastIndexOf("/")).contains(".")) {
            // path contains a filename, get the containing dir instead.
            processedPath = processedPath.substring(0, processedPath.lastIndexOf("/"));
        } else if (!processedPath.contains("/") && processedPath.contains(".")) {
            processedPath = "/";
        }
        return processedPath;
    }

    private String preprocessedRelativePath(String path) throws Exception {
        return commonPathPreprocess(path);
    }

    public String value() throws Exception {
        String absolutePath = preprocessedAbsPath(this.absPath);
        String relativePath = preprocessedRelativePath(this.unresolvedRelativePath);
        // build absolute path representing the given relative path
        List<String> absoluteParts = new ArrayList<String>(Arrays.asList(absolutePath.split("/", -1)));
        String[] relativeParts = relativePath.split("/");
        for (String relativePart : relativeParts) {
            if (!relativePart.equals(".")) {
                if (relativePart.equalsIgnoreCase("..")) {
                    // move up a level
                    absoluteParts.remove(absoluteParts.size() - 1);
                } else {
                    absoluteParts.add(relativePart);
                }
            }
        }
        absoluteParts.removeIf(String::isEmpty);
        return "/" + String.join("/", absoluteParts);
    }
}
