package com.finrisk.radar.risk.engine;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RiskRuleRegistry {
  private final List<RiskRule> rules;

  public RiskRuleRegistry(List<RiskRule> input) {
    List<RiskRule> sorted =
        input.stream().sorted(Comparator.comparingInt(RiskRule::priority)).toList();
    Set<Integer> priorities = new HashSet<>();
    Set<RiskRuleType> types = new HashSet<>();
    for (RiskRule r : sorted) {
      if (!priorities.add(r.priority()))
        throw new IllegalStateException("Duplicate risk rule priority: " + r.priority());
      if (!types.add(r.supports()))
        throw new IllegalStateException("Duplicate risk rule type: " + r.supports());
    }
    rules = List.copyOf(sorted);
  }

  public List<RiskRule> rules() {
    return rules;
  }
}
