package com.hadi.test;

/**
 * Stores the input stream for a GitHub repository.
 */
public class GitHubRepoInputStream {

    private String userName;
    private String repoName;
    private byte[] bytes;

    public GitHubRepoInputStream(String userName, String repoName, byte[] bytes) {
        this.userName = userName;
        this.repoName = repoName;
        this.bytes = bytes;
    }

    public GitHubRepoInputStream() {
    };

    public byte[] getBaos() {
        return bytes;
    }

    public void setBaos(final byte[] bytes) {
        this.bytes = bytes;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(final String repoName) {
        this.repoName = repoName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }
}