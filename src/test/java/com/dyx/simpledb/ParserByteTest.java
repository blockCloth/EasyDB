package com.dyx.simpledb;

import com.dyx.simpledb.backend.utils.Parser;
import org.junit.Test;

import java.util.Arrays;

/**
 * @User Administrator
 * @CreateTime 2024/9/4 15:54
 * @className com.dyx.simpledb.ParserByteTest
 */
public class ParserByteTest {

    @Test
    public void constraintByteTest(){
        byte[] bytes = Parser.constraintByte(true, false, true, true);
        System.out.println(Arrays.toString(bytes));
    }
}
