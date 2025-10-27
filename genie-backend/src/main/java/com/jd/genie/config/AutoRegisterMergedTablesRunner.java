package com.jd.genie.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

// 注意：DataAgentConfig / DataAgentModelConfig 位于 com.jd.genie.config.data 包下
import com.jd.genie.config.data.DataAgentConfig;
import com.jd.genie.config.data.DataAgentModelConfig;

/**
 * 将合并进 H2 的“新表”自动注册为可见的数据集；
 * 同时把现有的 table 型模型（如 sales_data）转换为 sql 型模型，保证前端预览能抽到列。
 *
 * 最小侵入：不改 DataAgent 的初始化流程，只在其读取配置前动态调整 modelList。
 */
@Component
@Order(1) // 在合并完成（@Order(0)）之后、DataAgentInitRunner 之前执行
@ConditionalOnProperty(prefix = "merge", name = "autoRegister", havingValue = "true", matchIfMissing = true)
public class AutoRegisterMergedTablesRunner implements ApplicationRunner {

  @Autowired private MergedTablesRegistry registry;
  @Autowired private DataAgentConfig dataAgentConfig;
  @Autowired @Qualifier("h2DataSource") private DataSource h2; // 从 H2 读取列信息

  @Override public void run(ApplicationArguments args) {
    List<String> tables = registry.snapshot();

    List<DataAgentModelConfig> list = dataAgentConfig.getModelList();
    if (list == null) {
      list = new ArrayList<>();
      dataAgentConfig.setModelList(list);
    }

    // ① 先把已有的 table 型模型统一转换为 sql 型（例如 sales_data）
    int converted = 0;
    for (DataAgentModelConfig m0 : new ArrayList<>(list)) {
      String tp = m0.getType();
      String content = m0.getContent(); // table 型时，这里是表名
      if (tp != null && tp.equalsIgnoreCase("table") && content != null && !content.isBlank()) {
        List<String> cols0 = fetchColumnsFromH2(content);
        if (!cols0.isEmpty()) {
          String selectSql0 = buildSelectSql(content, cols0);
          m0.setType("sql");
          m0.setContent(selectSql0);
          String r0 = m0.getRemark();
          m0.setRemark(((r0 == null || r0.isEmpty()) ? "" : r0 + " | ") + "auto-converted from table to sql");
          converted++;
          log("converted table model to SQL: " + content);
        } else {
          log("no columns found for existing model: " + content);
        }
      }
    }
    if (converted > 0) log("converted existing table models: " + converted);

    // ② 再将本次合并得到的“新增表”注册为 sql 型模型
    if (tables == null || tables.isEmpty()) {
      log("no new tables to register");
      return;
    }

    // 已存在的模型（按 content 去重；此时 content 已可能是 SQL 文本）
    Set<String> existing = list.stream()
        .map(DataAgentModelConfig::getContent)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    for (String t : tables) {
      if (t == null || t.isBlank()) continue;

      // 若已经存在一个指向同表名的模型（table 型在上一步被转为 sql，content 改为 select 文本）
      // 这里再做一次简单防重：检查是否已有 name 或 remark 中包含该表名
      boolean dup = list.stream().anyMatch(m -> t.equalsIgnoreCase(m.getContent()) ||
          (m.getName() != null && m.getName().contains(t)));
      if (dup) { log("skip already registered by name/content: " + t); continue; }

      List<String> cols = fetchColumnsFromH2(t);
      if (cols.isEmpty()) { log("no columns found for new table: " + t + ", skip"); continue; }

      String selectSql = buildSelectSql(t, cols);

      DataAgentModelConfig m = new DataAgentModelConfig();
      m.setName(t + "（合并表）");
      m.setId("t_autoreg_" + t);   // 全局唯一即可
      m.setType("sql");            // 用 sql 类型，DataAgent 会基于 SQL 做列元数据解析
      m.setContent(selectSql);
      m.setRemark("Auto-registered from MySQL merge at startup");

      list.add(m);
      log("registered SQL model for table: " + t);
    }
  }

  private List<String> fetchColumnsFromH2(String table) {
    List<String> cols = new ArrayList<>();
    try (Connection c = h2.getConnection()) {
      DatabaseMetaData md = c.getMetaData();
      try (ResultSet rs = md.getColumns(null, null, table, "%")) {
        while (rs.next()) cols.add(rs.getString("COLUMN_NAME"));
      }
    } catch (Exception e) {
      log("fetchColumns error for " + table + ": " + e.getMessage());
    }
    return cols;
  }

  private String buildSelectSql(String table, List<String> cols) {
    // H2 已启用 MODE=MySQL，反引号可用；对列名做简单转义，表名也加反引号以保守起见
    String colList = cols.stream()
        .map(col -> "`" + col.replace("`", "``") + "`")
        .collect(Collectors.joining(", "));
    String tbl = "`" + table.replace("`", "``") + "`";
    return "select " + colList + " from " + tbl;
  }

  private static void log(String s) { System.out.println("[AutoRegisterMergedTables] " + s); }
}