package com.github.guoyyit.federatedsearchjava.federation;

public class SnapshotExpiredException extends RuntimeException {

    public SnapshotExpiredException(String snapshotId) {
        super(snapshotId);
    }
}
