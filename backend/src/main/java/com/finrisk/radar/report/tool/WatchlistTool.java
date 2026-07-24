package com.finrisk.radar.report.tool;

import com.finrisk.radar.watchlist.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WatchlistTool {
  private final WatchlistService watchlists;

  public WatchlistTool(WatchlistService watchlists) {
    this.watchlists = watchlists;
  }

  public List<WatchlistItemResponse> load(Long userId) {
    return watchlists.getAll(userId).stream().limit(20).toList();
  }
}
