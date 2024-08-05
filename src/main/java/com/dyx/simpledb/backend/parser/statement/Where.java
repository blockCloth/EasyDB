package com.dyx.simpledb.backend.parser.statement;

import lombok.ToString;

@ToString
public class Where {
    public SingleExpression singleExp1;
    public SingleExpression singleExp2;
    public String logicOp;

    public Where() {}

    public Where(SingleExpression exp) {
        this.singleExp1 = exp;
    }
}
