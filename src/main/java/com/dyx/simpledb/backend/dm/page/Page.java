package com.dyx.simpledb.backend.dm.page;

public interface Page {
    void lock();
    void unlock();
    void release();
    void setDirty(boolean dirty);
    boolean isDirty();
    int getPageNumber();
    byte[] getData();
}
