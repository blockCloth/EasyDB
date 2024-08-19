package com.dyx.simpledb.backend.dm;

import com.dyx.simpledb.backend.common.AbstractCache;
import com.dyx.simpledb.backend.dm.dataItem.DataItem;
import com.dyx.simpledb.backend.dm.dataItem.DataItemImpl;
import com.dyx.simpledb.backend.dm.logger.Logger;
import com.dyx.simpledb.backend.dm.page.Page;
import com.dyx.simpledb.backend.dm.page.PageOne;
import com.dyx.simpledb.backend.dm.page.PageX;
import com.dyx.simpledb.backend.dm.pageCache.PageCache;
import com.dyx.simpledb.backend.dm.pageIndex.PageIndex;
import com.dyx.simpledb.backend.dm.pageIndex.PageInfo;
import com.dyx.simpledb.backend.tm.TransactionManager;
import com.dyx.simpledb.backend.utils.Panic;
import com.dyx.simpledb.backend.utils.Parser;
import com.dyx.simpledb.backend.utils.Types;
import com.dyx.simpledb.common.Error;

import java.util.Arrays;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl)super.get(uid);
        if(!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if(raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }

        PageInfo pi = null;
        for(int i = 0; i < 5; i ++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null) {
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);

            short offset = PageX.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);

        } finally {
            // 将取出的pg重新插入pIndex
            if(pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    @Override
    public void physicalDelete(Long uid) throws Exception {
        // 解析出页号和偏移量
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int) (uid & ((1L << 32) - 1));

        // 获取目标页
        Page pg = pc.getPage(pgno);
        try {
            // 获取页中的数据
            byte[] data = pg.getData();

            // 计算数据项的大小
            short size = Parser.parseShort(Arrays.copyOfRange(data, offset + DataItemImpl.OF_SIZE, offset + DataItemImpl.OF_DATA));
            int dataItemLength = DataItemImpl.OF_DATA + size;

            // 清除数据项的内容（将数据项所在区域的字节清零）
            Arrays.fill(data, offset, offset + dataItemLength, (byte) 0);

            // 更新该页的可用空间信息
            pIndex.add(pgno, PageX.getFreeSpace(pg));

        } finally {
            // 释放页
            pg.release();
        }
    }

    // 为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int)(uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    // 在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时时读入PageOne，并验证正确性
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for(int i = 2; i <= pageNumber; i ++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }
    
}
