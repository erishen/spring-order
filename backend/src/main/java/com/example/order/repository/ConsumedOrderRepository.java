package com.example.order.repository;

import com.example.order.model.ConsumedOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumedOrderRepository extends JpaRepository<ConsumedOrderEntity, String> {
    /** 最近消费的订单投影，按 consumed_at 倒序取前 20 条（读路径统一走共享表）。 */
    List<ConsumedOrderEntity> findTop20ByOrderByConsumedAtDesc();

    /** 最近一条消费记录（用于取最近消费时间）。 */
    Optional<ConsumedOrderEntity> findTopByOrderByConsumedAtDesc();
}
