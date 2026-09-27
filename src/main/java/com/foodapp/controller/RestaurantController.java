package com.foodapp.controller;

import com.foodapp.entity.MenuItem;
import com.foodapp.service.RestaurantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    /**
     * API endpoint: GET /restaurants/{restaurantId}/menu
     * Kiểm tra Lazy Loading:
     * - Lần 1: Cache Miss (~3000ms)
     * - Lần 2: Cache Hit (< 20ms)
     */
    @GetMapping("/{restaurantId}/menu")
    public ResponseEntity<Map<String, Object>> getMenu(@PathVariable Long restaurantId) {
        long start = System.currentTimeMillis();
        List<MenuItem> items = restaurantService.getMenuByRestaurantId(restaurantId);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("restaurantId", restaurantId);
        response.put("totalItems", items.size());
        response.put("responseTimeMs", duration);
        response.put("cacheStatus", duration > 500 ? "CACHE_MISS (Truy vấn DB + nạp cache)" : "CACHE_HIT (Lấy trực tiếp từ cache)");
        response.put("menu", items);

        return ResponseEntity.ok(response);
    }

    /**
     * API xóa cache hỗ trợ thử nghiệm lặp lại
     */
    @DeleteMapping("/{restaurantId}/menu/cache")
    public ResponseEntity<Map<String, String>> evictCache(@PathVariable Long restaurantId) {
        restaurantService.evictMenuCache(restaurantId);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Đã xóa cache cho restaurantId: " + restaurantId);
        return ResponseEntity.ok(response);
    }
}
