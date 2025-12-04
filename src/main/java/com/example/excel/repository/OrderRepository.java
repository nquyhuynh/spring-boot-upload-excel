package com.example.excel.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Validate orders in batch using SQL IN clause
     * Returns map of order_id -> qty for the requested order IDs only
     * This is memory-efficient as it only loads data for current batch
     */
    public Map<Integer, Integer> getOrdersByIds(Set<Integer> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new HashMap<>();
        }

        // Build IN clause with placeholders
        String inClause = orderIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = "SELECT order_id, qty FROM \"order\" WHERE order_id IN (" + inClause + ")";

        // Convert Set to array for JDBC parameters
        Object[] params = orderIds.toArray();

        Map<Integer, Integer> orderMap = new HashMap<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        for (Map<String, Object> row : rows) {
            Integer orderId = (Integer) row.get("order_id");
            Integer qty = (Integer) row.get("qty");
            orderMap.put(orderId, qty);
        }

        return orderMap;
    }
}
