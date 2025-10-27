package com.jd.genie.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Component
@Order(0) // 尽早执行
public class MysqlToH2MergeRunner implements ApplicationRunner {

  @Autowired @Qualifier("mysqlDataSource")
  private DataSource mysql;

  @Autowired @Qualifier("h2DataSource")
  private DataSource h2;

  @Autowired
  private MergedTablesRegistry mergedTablesRegistry;

  @Value("${merge.routeToMysqlTables:}")
  private String routeToMysqlTablesCsv; // comma-separated table names (case-insensitive)

  @Value("${spring.datasource-mysql.jdbc-url}")
  private String mysqlJdbcUrl;

  @Value("${spring.datasource-mysql.username}")
  private String mysqlUsername;

  @Value("${spring.datasource-mysql.password}")
  private String mysqlPassword;

  @Value("${merge.routeAll:false}")
  private boolean routeAll;

  @Value("${merge.copyTables:}")
  private String copyTablesCsv;

  @Value("${merge.routeForce:false}")
  private boolean routeForce;

  @Override public void run(ApplicationArguments args) throws Exception {
    try (Connection src = mysql.getConnection();
         Connection dst = h2.getConnection()) {
      dst.setAutoCommit(false);
      List<String> createdTables = new ArrayList<>();
      Set<String> routeSet = parseRouteTables(routeToMysqlTablesCsv);
      Set<String> copySet  = parseRouteTables(copyTablesCsv);
      log("RouteAll=" + routeAll + ", routeForce=" + routeForce + ", routeSet=" + routeSet + ", copySet=" + copySet);

      DatabaseMetaData mdSrc = src.getMetaData();
      String catalog = src.getCatalog();

      try (ResultSet tables = mdSrc.getTables(catalog, null, "%", new String[]{"TABLE"})) {
        while (tables.next()) {
          String table = tables.getString("TABLE_NAME");
          String tableLower = table.toLowerCase(Locale.ROOT);
          boolean shouldRoute = routeAll ? !copySet.contains(tableLower) : routeSet.contains(tableLower);
          if (shouldRoute) {
            if (existsInH2(dst, table)) {
              if (routeForce) {
                log("Route-force: drop existing then create LINKED: " + table);
                dropTable(dst, table);
                createLinkedTable(dst, table);
                if (!createdTables.contains(table)) createdTables.add(table);
              } else {
                log("Route-only table already exists in H2 (linked or physical), skip: " + table + " (set merge.routeForce=true to recreate)");
              }
            } else {
              log("Route-only (create H2 LINKED TABLE to MySQL): " + table);
              createLinkedTable(dst, table);
              createdTables.add(table); // let auto-registrar pick it up
            }
            continue; // skip physical create+copy
          }

          if (!existsInH2(dst, table)) {
            log("Create H2 table: " + table);
            try (Statement st = dst.createStatement()) {
              st.execute(generateCreateTableDDL(mdSrc, catalog, table));
            }
            createdTables.add(table);
            log("Copy data: " + table);
            copyTable(src, dst, table);
          } else {
            log("Skip existing table in H2: " + table);
          }
        }
      }
      dst.commit();
      mergedTablesRegistry.addAll(createdTables);
      log("Done");
    }
  }

  private boolean existsInH2(Connection h2c, String table) throws SQLException {
    try (ResultSet rs = h2c.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
      return rs.next();
    }
  }

  private String generateCreateTableDDL(DatabaseMetaData md, String catalog, String table) throws SQLException {
    StringBuilder sb = new StringBuilder("CREATE TABLE ");
    sb.append(quoteH2(table)).append(" (");
    List<String> colDefs = new ArrayList<>(), pkCols = new ArrayList<>();

    try (ResultSet cols = md.getColumns(catalog, null, table, "%")) {
      while (cols.next()) {
        String col = cols.getString("COLUMN_NAME");
        String type = cols.getString("TYPE_NAME");
        int size = cols.getInt("COLUMN_SIZE");
        int scale = cols.getInt("DECIMAL_DIGITS");
        String nullable = cols.getString("IS_NULLABLE");
        String isAuto = cols.getString("IS_AUTOINCREMENT");
        String h2Type = mapMysqlTypeToH2(type, size, scale);
        StringBuilder def = new StringBuilder(quoteH2(col)).append(" ").append(h2Type);
        if ("NO".equalsIgnoreCase(nullable)) def.append(" NOT NULL");
        if ("YES".equalsIgnoreCase(isAuto)) def.append(" AUTO_INCREMENT");
        colDefs.add(def.toString());
      }
    }
    try (ResultSet pks = md.getPrimaryKeys(catalog, null, table)) {
      while (pks.next()) pkCols.add(quoteH2(pks.getString("COLUMN_NAME")));
    }
    if (!pkCols.isEmpty()) colDefs.add("PRIMARY KEY (" + String.join(", ", pkCols) + ")");
    sb.append(String.join(", ", colDefs)).append(")");
    return sb.toString();
  }

  private String mapMysqlTypeToH2(String t, int size, int scale) {
    String type = t == null ? "" : t.toLowerCase(Locale.ROOT);
    switch (type) {
      case "bigint": return "BIGINT";
      case "int": case "integer": case "mediumint": return "INT";
      case "smallint": return "SMALLINT";
      case "tinyint": return size == 1 ? "BOOLEAN" : "SMALLINT";
      case "bit": return "BOOLEAN";
      case "decimal": case "numeric": return "DECIMAL(" + Math.max(size,1) + "," + Math.max(scale,0) + ")";
      case "double": case "double precision": return "DOUBLE";
      case "float": return "REAL";
      case "char": return "CHAR(" + Math.max(size,1) + ")";
      case "varchar": return "VARCHAR(" + (size > 0 ? size : 255) + ")";
      case "text": case "tinytext": case "mediumtext": case "longtext": case "json": return "CLOB";
      case "blob": case "tinyblob": case "mediumblob": case "longblob": case "binary": case "varbinary": return "BLOB";
      case "date": return "DATE";
      case "time": return "TIME";
      case "datetime": case "timestamp": return "TIMESTAMP";
      default: return "VARCHAR(255)";
    }
  }

  private void copyTable(Connection src, Connection dst, String table) throws SQLException {
    List<String> cols = new ArrayList<>();
    try (ResultSet rs = src.getMetaData().getColumns(src.getCatalog(), null, table, "%")) {
      while (rs.next()) cols.add(rs.getString("COLUMN_NAME"));
    }
    if (cols.isEmpty()) return;

    String mysqlCols = joinQuoted(cols, true), h2Cols = joinQuoted(cols, false);
    String selectSql = "SELECT " + mysqlCols + " FROM " + quoteMySQL(table);
    String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
    String insertSql = "INSERT INTO " + quoteH2(table) + " (" + h2Cols + ") VALUES (" + placeholders + ")";

    try (Statement st = src.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
      st.setFetchSize(1000);
      try (ResultSet rs = st.executeQuery(selectSql);
           PreparedStatement ps = dst.prepareStatement(insertSql)) {
        int batch = 0;
        while (rs.next()) {
          for (int i = 1; i <= cols.size(); i++) ps.setObject(i, rs.getObject(i));
          ps.addBatch();
          if (++batch % 1000 == 0) ps.executeBatch();
        }
        ps.executeBatch();
      }
    }
  }

  private static String joinQuoted(List<String> idents, boolean mysql) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < idents.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(mysql ? quoteMySQL(idents.get(i)) : quoteH2(idents.get(i)));
    }
    return sb.toString();
  }

  private Set<String> parseRouteTables(String csv) {
    if (csv == null || csv.trim().isEmpty()) return Collections.emptySet();
    Set<String> s = new HashSet<>();
    for (String t : csv.split(",")) {
      String x = t.trim().toLowerCase(Locale.ROOT);
      if (!x.isEmpty()) s.add(x);
    }
    return s;
  }

  private void createLinkedTable(Connection h2c, String table) throws SQLException {
    String ddl = "CREATE LINKED TABLE " + quoteH2(table) + "(" +
        "'com.mysql.cj.jdbc.Driver'," +
        "'" + q(mysqlJdbcUrl) + "'," +
        "'" + q(mysqlUsername) + "'," +
        "'" + q(mysqlPassword) + "'," +
        "'" + q(table) + "')";
    try (Statement st = h2c.createStatement()) {
      st.execute(ddl);
    }
  }

  private void dropTable(Connection h2c, String table) throws SQLException {
    try (Statement st = h2c.createStatement()) {
      st.execute("DROP TABLE IF EXISTS " + quoteH2(table));
    }
  }

  private static String q(String s) { return s == null ? "" : s.replace("'", "''"); }

  private static String quoteMySQL(String ident) { return "`" + ident.replace("`","``") + "`"; }
  private static String quoteH2(String ident)     { return "\"" + ident.replace("\"","\"\"") + "\""; }
  private static void log(String s) { System.out.println("[MysqlToH2MergeRunner] " + s); }
}