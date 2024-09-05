package com.dyx.simpledb.backend.im;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.dyx.simpledb.backend.common.SubArray;
import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.dm.dataItem.DataItem;
import com.dyx.simpledb.backend.im.Node.InsertAndSplitRes;
import com.dyx.simpledb.backend.im.Node.LeafSearchRangeRes;
import com.dyx.simpledb.backend.im.Node.SearchNextRes;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;
import com.dyx.simpledb.backend.utils.Parser;

public class BPlusTree {
    DataManager dm;
    long bootUid;
    DataItem bootDataItem;
    Lock bootLock;

    public static long create(DataManager dm) throws Exception {
        byte[] rawRoot = Node.newNilRootRaw();
        long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rawRoot);
        return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
    }

    public static BPlusTree load(long bootUid, DataManager dm) throws Exception {
        DataItem bootDataItem = dm.read(bootUid);
        assert bootDataItem != null;
        BPlusTree t = new BPlusTree();
        t.bootUid = bootUid;
        t.dm = dm;
        t.bootDataItem = bootDataItem;
        t.bootLock = new ReentrantLock();
        return t;
    }

    private long rootUid() {
        bootLock.lock();
        try {
            SubArray sa = bootDataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start, sa.start+8));
        } finally {
            bootLock.unlock();
        }
    }

    private void updateRootUid(long left, long right, long rightKey) throws Exception {
        bootLock.lock();
        try {
            byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
            long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
            bootDataItem.before();
            SubArray diRaw = bootDataItem.data();
            System.arraycopy(Parser.long2Byte(newRootUid), 0, diRaw.raw, diRaw.start, 8);
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);
        } finally {
            bootLock.unlock();
        }
    }

    private long searchLeaf(long nodeUid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        if(isLeaf) {
            return nodeUid;
        } else {
            long next = searchNext(nodeUid, key);
            return searchLeaf(next, key);
        }
    }

    private long searchNext(long nodeUid, long key) throws Exception {
        while(true) {
            Node node = Node.loadNode(this, nodeUid);
            SearchNextRes res = node.searchNext(key);
            node.release();
            if(res.uid != 0) return res.uid;
            nodeUid = res.siblingUid;
        }
    }

    public List<Long> search(long key) throws Exception {
        return searchRange(key, key);
    }

    public List<Long> searchRange(long leftKey, long rightKey) throws Exception {
        long rootUid = rootUid();
        long leafUid = searchLeaf(rootUid, leftKey);
        List<Long> uids = new ArrayList<>();
        while(true) {
            Node leaf = Node.loadNode(this, leafUid);
            LeafSearchRangeRes res = leaf.leafSearchRange(leftKey, rightKey);
            leaf.release();
            uids.addAll(res.uids);
            if(res.siblingUid == 0) {
                break;
            } else {
                leafUid = res.siblingUid;
            }
        }
        return uids;
    }

    public void insert(long key, long uid) throws Exception {
        long rootUid = rootUid();
        InsertRes res = insert(rootUid, uid, key);
        assert res != null;
        if(res.newNode != 0) {
            updateRootUid(rootUid, res.newNode, res.newKey);
        }
    }

    class InsertRes {
        long newNode, newKey;
    }

    private InsertRes insert(long nodeUid, long uid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        InsertRes res = null;
        if(isLeaf) {
            res = insertAndSplit(nodeUid, uid, key);
        } else {
            long next = searchNext(nodeUid, key);
            InsertRes ir = insert(next, uid, key);
            if(ir.newNode != 0) {
                res = insertAndSplit(nodeUid, ir.newNode, ir.newKey);
            } else {
                res = new InsertRes();
            }
        }
        return res;
    }

    private InsertRes insertAndSplit(long nodeUid, long uid, long key) throws Exception {
        while(true) {
            Node node = Node.loadNode(this, nodeUid);
            InsertAndSplitRes iasr = node.insertAndSplit(uid, key);
            node.release();
            if(iasr.siblingUid != 0) {
                nodeUid = iasr.siblingUid;
            } else {
                InsertRes res = new InsertRes();
                res.newNode = iasr.newSon;
                res.newKey = iasr.newKey;
                return res;
            }
        }
    }

    public void close() {
        bootDataItem.release();
    }

    // 唯一索引的插入
    public void insertUnique(long key, long uid) throws Exception {
        // 查找是否已存在相同的键，确保唯一性
        List<Long> existingValues = search(key);
        if (!existingValues.isEmpty()) {
            throw new Exception("Duplicate key, violates unique constraint");
        }

        long rootUid = rootUid();
        InsertRes res = insert(rootUid, uid, key);  // 继续执行插入
        if (res.newNode != 0) {
            updateRootUid(rootUid, res.newNode, res.newKey);
        }
    }

    public void update(long key, long newUid) throws Exception {
        List<Long> existingValues = search(key);
        if (existingValues.isEmpty()) {
            throw new Exception("Key not found, cannot update");
        }
        // 假设 B+ 树的唯一索引，搜索结果应该只有一个 uid
        long oldUid = existingValues.get(0);
        delete(key);  // 删除旧的键
        insertUnique(key, newUid);  // 插入新的键和新的 UID
    }

    public void delete(long key) throws Exception {
        long rootUid = rootUid();  // 获取根节点的UID
        boolean res = delete(rootUid, key);  // 从根节点递归删除指定的键

        // 如果根节点只有一个子节点，更新根节点
        if (res && rootUidHasSingleChild()) {
            updateRootAfterDeletion();  // 更新根节点为唯一子节点
        }
    }

    // 递归删除键
    private boolean delete(long nodeUid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);  // 加载当前节点
        boolean isLeaf = node.isLeaf();  // 判断当前节点是否为叶子节点
        node.release();

        if (isLeaf) {
            return deleteFromLeaf(nodeUid, key);  // 从叶子节点中删除键
        } else {
            long next = searchNext(nodeUid, key);  // 查找下一个可能包含目标键的子节点
            boolean res = delete(next, key);  // 递归删除子节点中的键

            // 如果删除成功，且节点需要合并，执行合并操作
            if (res && nodeNeedsMerge(nodeUid)) {
                mergeNodes(nodeUid, next);  // 合并或重分布节点
            }
            return res;
        }
    }

    // 从叶子节点中删除键
    private boolean deleteFromLeaf(long nodeUid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        int noKeys = Node.getRawNoKeys(node.raw);  // 获取当前节点的键数量
        int kth = -1;

        // 找到需要删除的键所在的索引
        for (int i = 0; i < noKeys; i++) {
            long ik = Node.getRawKthKey(node.raw, i);
            if (ik == key) {
                kth = i;
                break;
            }
        }

        if (kth == -1) {
            // 如果没有找到键，说明该节点不包含目标键，返回 false
            return false;
        }

        // 进行删除操作，调整键和值
        node.dataItem.before();  // 加锁，准备进行数据修改
        Node.shiftRawKth(node.raw, kth);  // 删除键并将后面的键左移
        Node.setRawNoKeys(node.raw, noKeys - 1);  // 更新节点的键数量
        node.dataItem.after(TransactionManagerImpl.SUPER_XID);  // 提交修改
        node.release();

        // 返回 true，表示删除成功
        return true;
    }

    // 判断节点是否需要合并
    private boolean nodeNeedsMerge(long nodeUid) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        int noKeys = Node.getRawNoKeys(node.raw);  // 获取当前节点的键数量
        node.release();
        // 如果节点的键数量小于平衡数量，返回 true 表示需要合并
        return noKeys < Node.BALANCE_NUMBER;
    }

    // 合并两个节点
    private void mergeNodes(long leftUid, long rightUid) throws Exception {
        Node left = Node.loadNode(this, leftUid);  // 加载左侧节点
        Node right = Node.loadNode(this, rightUid);  // 加载右侧节点

        int leftNoKeys = Node.getRawNoKeys(left.raw);  // 获取左节点的键数量
        int rightNoKeys = Node.getRawNoKeys(right.raw);  // 获取右节点的键数量

        // 将右节点的所有键和子节点合并到左节点中
        for (int i = 0; i < rightNoKeys; i++) {
            long key = Node.getRawKthKey(right.raw, i);  // 获取右节点的第 i 个键
            long son = Node.getRawKthSon(right.raw, i);  // 获取右节点的第 i 个子节点UID
            Node.setRawKthKey(left.raw, key, leftNoKeys + i);  // 将键插入左节点
            Node.setRawKthSon(left.raw, son, leftNoKeys + i);  // 将子节点插入左节点
        }

        // 更新左节点的键数量，并将其兄弟节点指针指向右节点的兄弟
        Node.setRawNoKeys(left.raw, leftNoKeys + rightNoKeys);  // 更新左节点的键数量
        Node.setRawSibling(left.raw, Node.getRawSibling(right.raw));  // 更新左节点的兄弟节点指针

        // 删除右节点
        left.dataItem.before();  // 加锁
        dm.physicalDelete(rightUid);  // 删除右节点的数据项
        left.dataItem.after(TransactionManagerImpl.SUPER_XID);  // 提交修改

        left.release();  // 释放左节点
        right.release();  // 释放右节点
    }

    // 更新根节点
    private void updateRootAfterDeletion() throws Exception {
        long rootUid = rootUid();
        Node root = Node.loadNode(this, rootUid);

        // 如果根节点是叶子节点，则不做任何操作
        if (root.isLeaf()) {
            root.release();
            return;
        }

        // 获取根节点的唯一子节点
        long onlyChildUid = Node.getRawKthSon(root.raw, 0);
        root.release();

        // 锁定根节点并更新其UID为唯一子节点的UID
        bootLock.lock();
        try {
            bootDataItem.before();  // 加锁
            SubArray diRaw = bootDataItem.data();
            System.arraycopy(Parser.long2Byte(onlyChildUid), 0, diRaw.raw, diRaw.start, 8);  // 更新根节点
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);  // 提交修改
        } finally {
            bootLock.unlock();
        }
    }

    // 判断根节点是否只有一个子节点
    private boolean rootUidHasSingleChild() throws Exception {
        long rootUid = rootUid();
        Node root = Node.loadNode(this, rootUid);
        boolean result = Node.getRawNoKeys(root.raw) == 1;  // 如果根节点只有一个子节点，返回 true
        root.release();
        return result;
    }
}
