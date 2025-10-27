package com.jd.genie.config;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MergedTablesRegistry {
  private final Set<String> created = Collections.synchronizedSet(new LinkedHashSet<>());

  public void add(String table) {
    if (table != null && !table.isBlank()) created.add(table);
  }
  public void addAll(Collection<String> tables) {
    if (tables != null) tables.forEach(this::add);
  }
  public List<String> snapshot() {
    synchronized (created) { return new ArrayList<>(created); }
  }
}