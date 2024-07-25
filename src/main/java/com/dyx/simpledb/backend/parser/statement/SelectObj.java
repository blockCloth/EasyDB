package com.dyx.simpledb.backend.parser.statement;

import lombok.ToString;

@ToString
public class SelectObj {
    public String tableName;
    public String[] fields;
    public Where where;
}
