package com.finrisk.radar.admin;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminPage<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {
  public static <S, T> AdminPage<T> from(Page<S> page, List<T> items) {
    return new AdminPage<>(
        items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
