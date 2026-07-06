package com.oac.sample.repository;

import com.oac.sample.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    List<Order> findByOwnerId(String ownerId);

    List<Order> findByStatus(String status);

    List<Order> findByTotalGreaterThan(double minTotal);
}