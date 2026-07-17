package com.example.order.repository;

import com.example.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {

    /** Used to derive "new user" status from real order history. */
    boolean existsByUserId(String userId);
}
