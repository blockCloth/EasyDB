package com.dyx.simpledb.backend.parser.statement;

import lombok.ToString;

@ToString
public class Create {
    public String tableName;
    public String[] fieldName;
    public String[] fieldType;
    public String[] index;
}
