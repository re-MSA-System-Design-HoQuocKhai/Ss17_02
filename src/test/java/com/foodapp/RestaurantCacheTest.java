package com.foodapp;

import com.foodapp.entity.MenuItem;
import com.foodapp.service.RestaurantService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest
class RestaurantCacheTest {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("Kiểm tra cơ chế Lazy Loading: Lần 1 Cache Miss (~3000ms), Lần 2 Cache Hit (<20ms)")
    void testLazyLoadingCacheable() {
        Long restaurantId = 101L;

        // Xóa cache cũ nếu có để đảm bảo kiểm thử chính xác
        restaurantService.evictMenuCache(restaurantId);

        // --- LẦN GỌI THỨ NHẤT: CACHE MISS ---
        long start1 = System.currentTimeMillis();
        List<MenuItem> menu1 = restaurantService.getMenuByRestaurantId(restaurantId);
        long duration1 = System.currentTimeMillis() - start1;

        System.out.println("=================================================");
        System.out.println(">>> LẦN 1 (CACHE MISS):");
        System.out.println("    Thời gian thực thi: " + duration1 + " ms");
        System.out.println("    Số lượng món ăn: " + menu1.size());
        System.out.println("=================================================");

        // Đánh giá lần 1: Thời gian phải >= 3000ms (do Thread.sleep(3000))
        Assertions.assertNotNull(menu1);
        Assertions.assertEquals(2, menu1.size());
        Assertions.assertTrue(duration1 >= 2900, "Lần 1 Cache Miss phải mất >= 3000ms do xử lý DB phức tạp");

        // Kiểm tra dữ liệu món ăn trong DB mẫu
        Assertions.assertEquals("Phở Bò", menu1.get(0).getDishName());
        Assertions.assertEquals("Bún Chả", menu1.get(1).getDishName());

        // --- KIỂM TRA KEY TRONG REDIS ---
        String cacheKey = "restaurantMenu::" + restaurantId;
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);

        System.out.println(">>> DỮ LIỆU REDIS CACHE KEY [" + cacheKey + "]:");
        System.out.println(cachedValue);
        Assertions.assertNotNull(cachedValue, "Key " + cacheKey + " phải tồn tại trong Redis sau lần gọi 1");
        Assertions.assertTrue(cachedValue.contains("Phở Bò"), "Cache phải chứa 'Phở Bò'");

        // --- LẦN GỌI THỨ HAI: CACHE HIT ---
        long start2 = System.currentTimeMillis();
        List<MenuItem> menu2 = restaurantService.getMenuByRestaurantId(restaurantId);
        long duration2 = System.currentTimeMillis() - start2;

        System.out.println("=================================================");
        System.out.println(">>> LẦN 2 (CACHE HIT):");
        System.out.println("    Thời gian thực thi: " + duration2 + " ms");
        System.out.println("    Số lượng món ăn: " + menu2.size());
        System.out.println("=================================================");

        // Đánh giá lần 2: Lấy từ cache, không chạy method body, thời gian < 100ms (thực tế trên máy ~5-15ms)
        Assertions.assertNotNull(menu2);
        Assertions.assertEquals(menu1.size(), menu2.size());
        Assertions.assertTrue(duration2 < 100, "Lần 2 Cache Hit phải trả về siêu tốc (< 100ms, kỳ vọng < 20ms)");

        // Dọn dẹp cache
        restaurantService.evictMenuCache(restaurantId);
    }
}
