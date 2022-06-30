package com.gerardnico.calcite;

import com.gerardnico.calcite.schema.hr.HrSchemaMin;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.externalize.RelWriterImpl;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.OracleSqlDialect;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.*;
import org.junit.Test;

import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This is class that demonstrate the query process planning:
 *   * parse
 *   * validate
 *   * convert to a query plan
 *   * execute.
 * See <a href="https://gerardnico.com/db/calcite/query_planning#example">Query planning process example for more info</a>
 */
public class CalciteFrameworksTest {



    @Test
    public void parseValidateAndLogicalPlanTest() throws SqlParseException, RelConversionException, ValidationException, SQLException {

        // Build the schema -- calcite构造一个根schema
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        // 创建暴露 属性、方法的 schema， 并初始化了数据emps 、 depts
        ReflectiveSchema schema = new ReflectiveSchema(new HrSchemaMin());
        // 走超类的add  org.apache.calcite.jdbc.CalciteSchema.SchemaPlusImpl.add()
        // 通过debug可以看到，最后返回的是一个SchemaPlusImpl,且schema属于rootSchema的sub（子）
        SchemaPlus hr = rootSchema.add("HR", schema);

        // Get a non-sensitive parser -- 设置大小写不敏感
        SqlParser.Config insensitiveParser = SqlParser.configBuilder()
                .setCaseSensitive(false)
                .build();

        // Build a global configuration object
        FrameworkConfig config = Frameworks.newConfigBuilder()
                // 设置sql 粘贴的配置
                .parserConfig(insensitiveParser)
                // 设置默认schema
                .defaultSchema(hr)
                .build();

        // Get the planner tool -- 获取查询计划
        Planner planner = Frameworks.getPlanner(config);

        // Parse the sql to a tree -- 通过查询计划粘贴SQL，转化为sqlNode
        SqlNode sqlNode = planner.parse("select depts.name, count(emps.empid) from emps inner join depts on emps.deptno = depts.deptno group by depts.deptno, depts.name order by depts.name");

        // Print it
        System.out.println(sqlNode.toSqlString(OracleSqlDialect.DEFAULT));

        // Validate the tree
        // 查询计划校验sqlNode
        SqlNode sqlNodeValidated = planner.validate(sqlNode);

        // Convert the sql tree to a relation expression
        // 将校验结束的sqlNode转为关系表达式
        RelRoot relRoot = planner.rel(sqlNodeValidated);

        // Explain, print the relational expression
        // 投影，较少不需要查询的属性
        RelNode relNode = relRoot.project();
        final RelWriter relWriter = new RelWriterImpl(new PrintWriter(System.out), SqlExplainLevel.ALL_ATTRIBUTES, false);
        // org.apache.calcite.rel.AbstractRelNode.explain 实际解释
        relNode.explain(relWriter);

        // Run it -- 预解析
        PreparedStatement run = RelRunners.run(relNode);
        ResultSet resultSet = run.executeQuery();

        // Print it
        System.out.println("Result:");
        while (resultSet.next()) {
            for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                System.out.print(resultSet.getObject(i)+",");
            }
            System.out.println();
        }

    }
}
