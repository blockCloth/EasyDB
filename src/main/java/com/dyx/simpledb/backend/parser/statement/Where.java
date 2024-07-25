package com.dyx.simpledb.backend.parser.statement;

import lombok.ToString;

@ToString
public class Where {
    public SingleExpression singleExp1;
    public String logicOp;
    public SingleExpression singleExp2;
}
