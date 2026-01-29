package com.hadi.clarpse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a common directory.
 */
public class CommonDir {

    private static final Logger LOGGER = LogManager.getLogger(CommonDir.class);
    private final String[] dirs;

    public CommonDir(String... dirs) {
        if (dirs == null) {
            this.dirs = new String[0];
        } else {
            this.dirs = Arrays.copyOf(dirs, dirs.length);
        }
        for (String dir : this.dirs) {
            if (!dir.contains("/")) {
                throw new IllegalArgumentException("Directory path: " + dir + " is invalid!");
            }
        }
    }

    public String value() throws Exception {
        if (dirs.length < 1) {
            throw new Exception("No dirs were supplied!");
        } else {
            String commonDir = dirs[0];
            for (String dir : dirs) {
                String[] dirAParts = commonDir.split("/");
                String[] dirBParts = dir.split("/");
                List<String> matchingParts = new ArrayList<String>();
                int j = 0;
                while (j < dirAParts.length && j < dirBParts.length && dirAParts[j].equals(dirBParts[j])) {
                    matchingParts.add(dirAParts[j]);
                    j++;
                }
                List<String> filteredMatchingParts =
                    matchingParts.stream().filter(s -> !s.contains(".")).collect(Collectors.toList());
                commonDir = StringUtils.join(filteredMatchingParts, "/");
            }
            if (commonDir.isEmpty()) {
                commonDir = "/";
            }
            LOGGER.info("Found common dir: " + commonDir + " for dirs: " + Arrays.toString(this.dirs));
            return commonDir;
        }
    }
}
