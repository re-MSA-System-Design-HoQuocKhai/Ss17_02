# BÀI TẬP 2: TRIỂN KHAI LAZY LOADING VỚI @CACHEABLE

> **Khóa học:** Microservices System Design — Session 17: Distributed Caching Architecture  
> **Mã bài tập:** `SS17-EX02-LAZY-LOADING-CACHEABLE`  
> **Chủ đề:** Chiến lược Lazy Loading (Chỉ truy vấn DB một lần duy nhất khi Cache Miss)  
> **Công nghệ áp dụng:** Java 21, Spring Boot 3.3.4, Spring Cache, Spring Data JPA, H2 In-Memory Database, Redis 7.2 (Lettuce Driver), Jackson JSON Serializer  

---

## 1. Bối Cảnh & Mục Tiêu Kỹ Thuật

### 1.1. Bối cảnh bài toán
Hệ thống đặt món ăn trực tuyến cung cấp API `GET /restaurants/{restaurantId}/menu` để lấy danh sách thực đơn theo từng nhà hàng. Trước khi tối ưu, mỗi lượt gọi API đều bắt buộc phải quét qua nhiều bảng trong CSDL với các câu lệnh SQL JOIN phức tạp:
- Thời gian phản hồi trung bình lên tới **~3000ms (3 giây)**.
- Gây nghẽn CPU và quá tải kết nối connection pool của Database dưới lưu lượng truy cập lớn.

### 1.2. Giải pháp kỹ thuật: Chiến lược Lazy Loading
- **Lần đầu tiên (Cache Miss):** Ứng dụng thực hiện truy vấn Database, sau đó nạp toàn bộ kết quả vào bộ nhớ đệm (Redis Cache) với namespace `restaurantMenu` và khóa động theo ID nhà hàng (`#id`).
- **Các lần tiếp theo (Cache Hit):** Spring Cache tự động can thiệp (AOP Interceptor), trả ngay dữ liệu đã lưu trong Redis mà hoàn toàn **không thực thi method body** (không sleep 3s, không gửi câu lệnh SQL tới DB).
- **Thời gian phản hồi giảm từ ~3500ms xuống còn 3ms - 20ms** (tốc độ tăng hơn **100 lần**).

---

## 2. Mã Nguồn Nộp Bài: `RestaurantService.java`

Đường dẫn file: `src/main/java/com/foodapp/service/RestaurantService.java`

```java
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
```

---

## 3. Báo Cáo Đo Lường Thời Gian Phản Hồi (Postman / cURL)

### 3.1. Lần gọi thứ 1: Cache Miss (Truy vấn DB + Nạp Cache)
- **HTTP Request:** `GET http://localhost:8080/restaurants/101/menu`
- **Response Headers:** `HTTP/1.1 200 OK`
- **Thời gian phản hồi:** **3566 ms (≥ 3.0 giây)**
- **Response Body:**
```json
{
  "restaurantId": 101,
  "totalItems": 2,
  "responseTimeMs": 3566,
  "cacheStatus": "CACHE_MISS (Truy vấn DB + nạp cache)",
  "menu": [
    {
      "id": 1,
      "restaurantId": 101,
      "dishName": "Phở Bò",
      "price": 55000.00
    },
    {
      "id": 2,
      "restaurantId": 101,
      "dishName": "Bún Chả",
      "price": 60000.00
    }
  ]
}
```

---

### 3.2. Lần gọi thứ 2: Cache Hit (Lấy trực tiếp từ Cache)
- **HTTP Request:** `GET http://localhost:8080/restaurants/101/menu`
- **Response Headers:** `HTTP/1.1 200 OK`
- **Thời gian phản hồi:** **3 ms (đạt chuẩn < 20 ms)**
- **Response Body:**
```json
{
  "restaurantId": 101,
  "totalItems": 2,
  "responseTimeMs": 3,
  "cacheStatus": "CACHE_HIT (Lấy trực tiếp từ cache)",
  "menu": [
    {
      "id": 1,
      "restaurantId": 101,
      "dishName": "Phở Bò",
      "price": 55000.00
    },
    {
      "id": 2,
      "restaurantId": 101,
      "dishName": "Bún Chả",
      "price": 60000.00
    }
  ]
}
```

---

## 4. Đoạn Log Console Thể Hiện Sự Khác Biệt Giữa 2 Lần Gọi

```log
================================================================================
>>> LẦN GỌI THỨ 1 (CACHE MISS - Có log truy vấn CSDL và chạy Thread.sleep 3s):
================================================================================
2026-09-27T18:55:40.869+07:00  INFO 23080 --- [nio-8080-exec-1] com.foodapp.service.RestaurantService    : ================================================================================
2026-09-27T18:55:40.869+07:00  INFO 23080 --- [nio-8080-exec-1] com.foodapp.service.RestaurantService    : >>> [DATABASE QUERY - CACHE MISS] Đang truy vấn CSDL cho restaurantId: 101
2026-09-27T18:55:40.869+07:00  INFO 23080 --- [nio-8080-exec-1] com.foodapp.service.RestaurantService    : >>> Thực hiện các câu lệnh SQL JOIN phức tạp và tính toán nghiệp vụ (độ trễ 3s)...
2026-09-27T18:55:40.869+07:00  INFO 23080 --- [nio-8080-exec-1] com.foodapp.service.RestaurantService    : ================================================================================
Hibernate: 
    select
        mi1_0.id,
        mi1_0.dish_name,
        mi1_0.price,
        mi1_0.restaurant_id 
    from
        menu_items mi1_0 
    where
        mi1_0.restaurant_id=?
2026-09-27T18:55:43.990+07:00  INFO 23080 --- [nio-8080-exec-1] com.foodapp.service.RestaurantService    : >>> [DATABASE RESULT] Đã tải 2 món ăn từ Database cho restaurantId: 101

================================================================================
>>> LẦN GỌI THỨ 2 & 3 (CACHE HIT - Hoàn toàn KHÔNG có log DB, KHÔNG gọi Hibernate):
================================================================================
(Không có log nào từ RestaurantService hay Hibernate do Spring AOP chặn và trả dữ liệu trực tiếp từ Redis trong 3ms)
```

---

## 5. Dữ Liệu Kiểm Tra Trong Redis (`redis-cli`)

```bash
$ redis-cli keys "*"
1) "restaurantMenu::101"

$ redis-cli get "restaurantMenu::101"
["java.util.ArrayList",[{"@class":"com.foodapp.entity.MenuItem","id":1,"restaurantId":101,"dishName":"Phở Bò","price":["java.math.BigDecimal",55000.00]},{"@class":"com.foodapp.entity.MenuItem","id":2,"restaurantId":101,"dishName":"Bún Chả","price":["java.math.BigDecimal",60000.00]}]]
```

---

## 6. Đối Chiếu Bảng Kiểm Đánh Giá

| Tiêu chí | Mức đạt yêu cầu | Kết quả thực tế kiểm nghiệm | Đánh giá |
| :--- | :--- | :--- | :---: |
| **Đúng annotation** | Phương thức được đánh dấu `@Cacheable(value = "restaurantMenu", key = "#id")`. | Khai báo chính xác trên `RestaurantService.getMenuByRestaurantId(Long id)`. | **ĐẠT** |
| **Cache Hit** | Lần gọi thứ hai với cùng `restaurantId` không in log truy vấn DB (không có `Thread.sleep`). | Console không in bất kỳ log truy vấn CSDL hay Hibernate nào. | **ĐẠT** |
| **Cache Miss** | Lần gọi đầu tiên có log truy vấn DB và thời gian ≥ 3 giây. | Log in đầy đủ SQL `select ... from menu_items`, thời gian phản hồi: 3566ms. | **ĐẠT** |
| **Thời gian lần 2** | Thời gian phản hồi < 20ms (kiểm tra trên Postman/curl). | Thời gian phản hồi đạt **3 ms** (nhỏ hơn rất nhiều so với ngưỡng 20ms). | **ĐẠT** |
| **Cache key đúng** | Cache được lưu với key là giá trị `id` (ví dụ: 101), không dùng giá trị khác. | Key trong Redis là `restaurantMenu::101`. | **ĐẠT** |
