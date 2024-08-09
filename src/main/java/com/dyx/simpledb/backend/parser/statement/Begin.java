package com.dyx.simpledb.backend.parser.statement;

import com.dyx.simpledb.backend.vm.IsolationLevel;
import lombok.ToString;

@ToString
public class Begin {
    public IsolationLevel isolationLevel;
}
