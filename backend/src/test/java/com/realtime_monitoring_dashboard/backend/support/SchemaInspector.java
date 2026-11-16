package com.realtime_monitoring_dashboard.backend.support;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

public final class SchemaInspector {

    private static final String INDEX_COLUMNS = """
            select a.attname
            from pg_index i
            join pg_class c on c.oid = i.indexrelid
            cross join lateral unnest(i.indkey::int2[]) with ordinality as k(attnum, ord)
            join pg_attribute a on a.attrelid = i.indrelid and a.attnum = k.attnum
            where c.relname = ?
            order by k.ord
            """;

    private static final String UNIQUE_CONSTRAINTS = """
            select count(*)
            from information_schema.table_constraints tc
            join information_schema.key_column_usage kcu
              on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
            where tc.constraint_type = 'UNIQUE'
              and tc.table_schema = 'public'
              and tc.table_name = ?
              and kcu.column_name = ?
            """;

    private static final String COLUMN_DEFAULT = """
            select column_default
            from information_schema.columns
            where table_schema = 'public' and table_name = ? and column_name = ?
            """;

    private static final String IS_NULLABLE = """
            select is_nullable
            from information_schema.columns
            where table_schema = 'public' and table_name = ? and column_name = ?
            """;

    private final JdbcTemplate jdbc;

    public SchemaInspector(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public List<String> indexColumns(String indexName) {
        return jdbc.queryForList(INDEX_COLUMNS, String.class, indexName);
    }

    public int uniqueConstraintCount(String table, String column) {
        Integer count = jdbc.queryForObject(UNIQUE_CONSTRAINTS, Integer.class, table, column);
        return count == null ? 0 : count;
    }

    public String columnDefault(String table, String column) {
        return jdbc.queryForObject(COLUMN_DEFAULT, String.class, table, column);
    }

    public boolean isNullable(String table, String column) {
        return "YES".equals(jdbc.queryForObject(IS_NULLABLE, String.class, table, column));
    }
}