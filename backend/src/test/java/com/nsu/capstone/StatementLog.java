package com.nsu.capstone;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** DataSource를 지나간 SQL 문장(prepareStatement 호출)을 순서대로 모은다. */
public final class StatementLog {

    private final List<String> statements = new CopyOnWriteArrayList<>();

    void record(String sql) {
        statements.add(sql);
    }

    public void clear() {
        statements.clear();
    }

    public List<String> statements() {
        return List.copyOf(statements);
    }

    public long count() {
        return statements.size();
    }

    /** 이름이 들어 있는 문장 수. 표 이름 등으로 특정 조회만 센다. */
    public long countContaining(String fragment) {
        return statements.stream().filter(sql -> sql.contains(fragment)).count();
    }
}
