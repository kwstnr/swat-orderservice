package ch.hslu.swda.g06.order.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import ch.hslu.swda.g06.order.model.Order;

public interface IOrderRepository extends MongoRepository<Order, String> {
}
