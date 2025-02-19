package com.alibaba.druid.sql.dialect.starrocks.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLPartitionOf;
import com.alibaba.druid.sql.ast.expr.SQLArrayExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.dialect.starrocks.ast.statement.StarRocksCreateResourceStatement;
import com.alibaba.druid.sql.dialect.starrocks.ast.statement.StarRocksCreateTableStatement;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.util.FnvHash;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StarRocksOutputVisitor extends SQLASTOutputVisitor implements StarRocksASTVisitor {
    {
        this.dbType = DbType.starrocks;
        this.shardingSupport = true;
        this.quote = '`';
    }

    public StarRocksOutputVisitor(StringBuilder appender) {
        super(appender);
    }

    public StarRocksOutputVisitor(StringBuilder appender, DbType dbType) {
        super(appender, dbType);
    }

    public StarRocksOutputVisitor(StringBuilder appender, boolean parameterized) {
        super(appender, parameterized);
    }

    @Override
    protected void printEngine(SQLCreateTableStatement x) {
        if (x instanceof StarRocksCreateTableStatement) {
            SQLExpr engine = ((StarRocksCreateTableStatement) x).getEngine();
            if (engine != null) {
                print0(ucase ? " ENGINE = " : " engine = ");
                engine.accept(this);
            }
        }
    }

    public boolean visit(StarRocksCreateTableStatement x) {
        printCreateTable(x, false);

        SQLPartitionOf partitionOf = x.getPartitionOf();
        if (partitionOf != null) {
            println();
            print0(ucase ? "PARTITION OF " : "partition of ");
            partitionOf.accept(this);
        }
        printEngine(x);
        printPartitionBy(x);
        printTableOptions(x);

        SQLName tablespace = x.getTablespace();
        if (tablespace != null) {
            println();
            print0(ucase ? "TABLESPACE " : "tablespace ");
            tablespace.accept(this);
        }

        SQLName model = x.getAggDuplicate();
        if (model != null) {
            println();
            String modelName = model.getSimpleName().toLowerCase();
            switch (modelName) {
                case "duplicate":
                    print0(ucase ? "DUPLICATE" : "duplicate");
                    break;
                case "aggregate":
                    print0(ucase ? "AGGREGATE" : "aggregate");
                    break;
                default:
                    break;
            }
            print(' ');
            print0(ucase ? "KEY" : "key");
            if (x.getAggDuplicateParameters().size() > 0) {
                for (int i = 0; i < x.getAggDuplicateParameters().size(); ++i) {
                    if (i != 0) {
                        println(", ");
                    }
                    SQLExpr sqlExpr = x.getAggDuplicateParameters().get(i);
                    if (!sqlExpr.toString().startsWith("(") && !sqlExpr.toString().startsWith("`")) {
                        print0("(");
                        sqlExpr.accept(this);
                        print0(")");
                    } else {
                        sqlExpr.accept(this);
                    }
                }
            }
        } else if (x.isPrimary()) {
            println();
            print0(ucase ? "PRIMARY" : "primary");
            print(' ');
            print0(ucase ? "KEY" : "key");
            if (x.getPrimaryUniqueParameters().size() > 0) {
                for (int i = 0; i < x.getPrimaryUniqueParameters().size(); ++i) {
                    if (i != 0) {
                        println(", ");
                    }
                    SQLExpr sqlExpr = x.getPrimaryUniqueParameters().get(i);
                    if (!sqlExpr.toString().startsWith("(") && !sqlExpr.toString().startsWith("`")) {
                        print0("(");
                        sqlExpr.accept(this);
                        print0(")");
                    } else {
                        sqlExpr.accept(this);
                    }
                }
            }
        } else if (x.isUnique()) {
            println();
            print0(ucase ? "UNIQUE" : "unique");
            print(' ');
            print0(ucase ? "KEY" : "key");
            if (x.getPrimaryUniqueParameters().size() > 0) {
                for (int i = 0; i < x.getPrimaryUniqueParameters().size(); ++i) {
                    if (i != 0) {
                        println(", ");
                    }
                    SQLExpr sqlExpr = x.getPrimaryUniqueParameters().get(i);
                    if (!sqlExpr.toString().startsWith("(") && !sqlExpr.toString().startsWith("`")) {
                        print0("(");
                        sqlExpr.accept(this);
                        print0(")");
                    } else {
                        sqlExpr.accept(this);
                    }
                }
            }
        }

//        if (x.getComment() != null) {
//            println();
//            print0(ucase ? "COMMENT " : "comment ");
//            print0(x.getComment().toString());
//        }

        List<SQLExpr> partitionBy = x.getPartitionBy();
        if (partitionBy != null && partitionBy.size() > 0) {
            println();
            print0(ucase ? "PARTITION BY " : "partition by ");
            x.getPartitionByName().accept(this);

            print0("(");
            for (int i = 0; i < partitionBy.size(); i++) {
                partitionBy.get(i).accept(this);
                if (i != partitionBy.size() - 1) {
                    print0(",");
                }
            }
            print0(")");

            println();
            print0("(");
            println();
            if (x.isLessThan()) {
                Map<SQLExpr, SQLExpr> lessThanMap = x.getLessThanMap();
                Set<SQLExpr> keySet = lessThanMap.keySet();
                int size = keySet.size();
                if (size > 0) {
                    int i = 0;
                    for (SQLExpr key : keySet) {
                        if (i != 0) {
                            println(", ");
                        }
                        SQLObject value = lessThanMap.get(key);
                        print0(ucase ? "  PARTITION " : "  partition ");
                        key.accept(this);
                        print0(ucase ? " VALUES LESS THAN " : " values less than ");
                        String s = value.toString();
                        if (s.startsWith("MAXVALUE")) {
                            value.accept(this);
                        } else {
//                            print0("(");
                            value.accept(this);
//                            print0(")");
                        }
                        i++;
                    }
                }
            } else if (x.isFixedRange()) {
                Map<SQLExpr, List<SQLExpr>> fixedRangeMap = x.getFixedRangeMap();
                Set<SQLExpr> keySet = fixedRangeMap.keySet();
                int size = keySet.size();
                if (size > 0) {
                    int i = 0;
                    for (SQLExpr key : keySet) {
                        List<SQLExpr> valueList = fixedRangeMap.get(key);
                        int listSize = valueList.size();

                        print0(ucase ? "  PARTITION " : "  partition ");
                        key.accept(this);
                        print0(ucase ? " VALUES " : " values ");
                        print0("[");

                        for (int j = 0; j < listSize; ++j) {
                            SQLExpr sqlExpr = valueList.get(j);
                            String[] split = sqlExpr.toString().split(",");

                            if (split.length <= 1) {
                                print0("(");
                                sqlExpr.accept(this);
                                print0(")");
                            } else {
                                sqlExpr.accept(this);
                            }

                            if (j != listSize - 1) {
                                print0(",");
                            }

                        }
                        print0(")");

                        if (i != size - 1) {
                            print0(",");
                            println();
                        }
                        i++;

                    }
                }
            } else if (x.isStartEnd()) {
                if (x.getStart() != null) {
                    print0(ucase ? "  START " : "  start ");
                    print0("(");
                    x.getStart().accept(this);
                    print0(")");
                }
                if (x.getEnd() != null) {
                    print0(ucase ? "  END " : "  end ");
                    print0("(");
                    x.getEnd().accept(this);
                    print0(")");
                }
                if (x.getEvery() != null) {
                    print0(ucase ? "  EVERY " : "  every ");
                    print0("(");
                    x.getEvery().accept(this);
                    print0(")");
                }

            }
            println();
            print0(")");
        }
        if (x.getDistributedBy() != null) {
            println();
            print0(ucase ? "DISTRIBUTED BY " : "distributed by ");
            switch (x.getDistributedBy().toString().toUpperCase()) {
                case "HASH": {
                    print0(ucase ? "HASH" : "hash");
                    break;
                }
                case "RANDOM": {
                    print0(ucase ? "RANDOM" : "random");
                    break;
                }
                default: {
                    break;
                }
            }
            if (x.getDistributedByParameters().size() > 0) {
                for (int i = 0; i < x.getDistributedByParameters().size(); ++i) {
                    if (i != 0) {
                        println(", ");
                    }
                    SQLExpr sqlExpr = x.getDistributedByParameters().get(i);
                    if (!sqlExpr.toString().startsWith("(")) {
                        print0("(");
                        sqlExpr.accept(this);
                        print0(")");
                    } else {
                        sqlExpr.accept(this);
                    }
                }
            }
            print0(ucase ? " BUCKETS " : "buckets ");
            int buckets = x.getBuckets();
            print0(String.valueOf(buckets));
        }

        if (x.getOrderBy() != null && x.getOrderBy().size() > 0) {
            println();
            print0(ucase ? "ORDER BY " : "order by ");
            for (int i = 0; i < x.getOrderBy().size(); ++i) {
                if (i != 0) {
                    println(", ");
                }
                SQLExpr sqlExpr = x.getOrderBy().get(i);
                if (!sqlExpr.toString().startsWith("(")) {
                    print0("(");
                    sqlExpr.accept(this);
                    print0(")");
                } else {
                    sqlExpr.accept(this);
                }
            }

        }

        int propertiesSize = x.getPropertiesMap().size();
        int lBracketSize = x.getlBracketPropertiesMap().size();
        if (propertiesSize > 0 || lBracketSize > 0) {
            println();
            print0(ucase ? "PROPERTIES " : "properties ");
            print0("(");
            if (propertiesSize > 0) {
                Map<SQLCharExpr, SQLCharExpr> propertiesMap = x.getPropertiesMap();
                Set<SQLCharExpr> keySet = propertiesMap.keySet();
                int i = 0;
                for (SQLCharExpr key : keySet) {
                    println();
                    print0("  ");
                    print0(key.getText());
                    print0(" = ");
                    print0(propertiesMap.get(key).getText());
                    if (lBracketSize > 0 || i != keySet.size() - 1) {
                        print0(",");
                    }
                    i++;
                }
            }

            if (lBracketSize > 0) {
                Map<SQLCharExpr, SQLCharExpr> lBracketPropertiesMap = x.getlBracketPropertiesMap();
                Set<SQLCharExpr> keySet = lBracketPropertiesMap.keySet();
                int i = 0;
                for (SQLCharExpr key : keySet) {
                    println();
                    print0("  ");
                    print0("[");
                    print0(key.getText());
                    print0(" = ");
                    print0(lBracketPropertiesMap.get(key).getText());
                    if (i != keySet.size() - 1) {
                        print0(",");
                    }
                    print0("]");
                    i++;
                }
            }
            println();
            print0(")");
        }

        SQLSelect select = x.getSelect();
        if (select != null) {
            println();
            print0(ucase ? "AS" : "as");

            println();
            visit(select);
        }

        return false;
    }

    protected void print(List<? extends SQLExpr> exprList) {
        int size = exprList.size();
        if (size == 0) {
            return;
        }

        print0(" (");

        this.indentCount++;
        println();
        for (int i = 0; i < size; ++i) {
            SQLExpr element = exprList.get(i);

            if (element instanceof SQLArrayExpr) {
                SQLArrayExpr array = ((SQLArrayExpr) element);
                SQLExpr expr = array.getExpr();

                if (expr instanceof SQLIdentifierExpr
                        && ((SQLIdentifierExpr) expr).nameHashCode64() == FnvHash.Constants.ARRAY
                        && printNameQuote
                ) {
                    print0(((SQLIdentifierExpr) expr).getName());
                } else if (expr != null) {
                    expr.accept(this);
                }

                print('[');
                printAndAccept(array.getValues(), ", ");

                if (i != size - 1) {
                    print0(",");
                }

                print(']');
            } else {
                element.accept(this);
            }

            if (i != size - 1 && !(element instanceof SQLArrayExpr)) {
                print(',');
            }

            if (i != size - 1) {
                println();
            }
        }
        this.indentCount--;
        println();
        print(')');
    }

    public boolean visit(SQLColumnDefinition x) {
        super.visit((SQLColumnDefinition) x);
        if (x.getAggType() != null) {
            print(' ');
            print0(ucase ? x.getAggType().getText().toUpperCase(Locale.ROOT) : x.getAggType().getText().toLowerCase(Locale.ROOT));
        }
        if (x.getBitmap() != null) {
            print(' ');
            print0(ucase ? "USING " : "using ");
            print0(ucase ? x.getBitmap().getText().toUpperCase(Locale.ROOT) : x.getBitmap().getText().toLowerCase(Locale.ROOT));
        }
        if (x.getIndexComment() != null) {
            print(' ');
            print0(ucase ? "COMMENT " : "comment ");
            x.getIndexComment().accept(this);
        }
        return false;
    }

    public boolean visit(StarRocksCreateResourceStatement x) {
        print0(ucase ? "CREATE " : "create ");
        if (x.isExternal()) {
            print0(ucase ? "EXTERNAL " : "external ");
        }

        print0(ucase ? "RESOURCE " : "resource ");
        x.getName().accept(this);
        println();

        print0(ucase ? "PROPERTIES" : "properties");
        print(x.getProperties());
        return false;
    }
}
