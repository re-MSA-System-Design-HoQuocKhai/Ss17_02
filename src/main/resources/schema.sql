CREATE TABLE IF NOT EXISTS menu_items (
    id BIGINT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    dish_name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL
);
