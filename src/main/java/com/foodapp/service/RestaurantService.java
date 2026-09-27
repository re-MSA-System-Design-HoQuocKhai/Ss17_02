package com.foodapp.service;

import com.foodapp.entity.MenuItem;
import com.foodapp.repository.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service xử lý thực đơn nhà hàng với chiến lược Lazy Loading qua @Cacheable.
 */
@Service
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final MenuItemRepository menuItemRepository;

    public RestaurantService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    /**
     * Lấy danh sách thực đơn theo ID nhà hàng.
     * Áp dụng chiến lược Lazy Loading với @Cacheable:
     * - Lần đầu (Cache Miss): Thực hiện truy vấn Database (mô phỏng trễ 3000ms),
     *   kết quả được lưu vào cache "restaurantMenu" với key là #id.
     * - Các lần sau (Cache Hit): Bỏ qua toàn bộ phần thân method (không sleep, không query DB),
     *   trả dữ liệu trực tiếp từ cache (< 20ms).
     *
     * @param id ID của nhà hàng (ví dụ: 101)
     * @return Danh sách món ăn của nhà hàng
     */
    @Cacheable(value = "restaurantMenu", key = "#id")
    public List<MenuItem> getMenuByRestaurantId(Long id) {
        log.info("================================================================================");
        log.info(">>> [DATABASE QUERY - CACHE MISS] Đang truy vấn CSDL cho restaurantId: {}", id);
        log.info(">>> Thực hiện các câu lệnh SQL JOIN phức tạp và tính toán nghiệp vụ (độ trễ 3s)...");
        log.info("================================================================================");

        try {
            // Giả lập thời gian xử lý phức tạp của Database (3000ms) theo yêu cầu đề bài
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Truy vấn CSDL bị gián đoạn", e);
        }

        // Truy vấn từ H2 Database
        List<MenuItem> items = menuItemRepository.findByRestaurantId(id);

        // Dự phòng fallback dữ liệu mẫu nếu chưa có trong DB
        if (items == null || items.isEmpty()) {
            items = new ArrayList<>();
            items.add(new MenuItem(1L, id, "Phở Bò", new BigDecimal("55000")));
            items.add(new MenuItem(2L, id, "Bún Chả", new BigDecimal("60000")));
        }

        log.info(">>> [DATABASE RESULT] Đã tải {} món ăn từ Database cho restaurantId: {}", items.size(), id);
        return items;
    }

    /**
     * Hỗ trợ xóa cache khi cần làm mới thực đơn hoặc kiểm thử
     */
    @CacheEvict(value = "restaurantMenu", key = "#id")
    public void evictMenuCache(Long id) {
        log.info(">>> [CACHE EVICT] Đã xóa cache restaurantMenu cho restaurantId: {}", id);
    }
}
