package com.example.shop_pet.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shop_pet.dto.AuthOrderItem;
import com.example.shop_pet.models.Order;
import com.example.shop_pet.models.OrderItem;
import com.example.shop_pet.models.Product;
import com.example.shop_pet.services.Order.OrderService;
import com.example.shop_pet.services.Product.ProductService;
import com.example.shop_pet.services.User.UserService;
import com.example.shop_pet.utils.JwtUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
  private final Long ORDER_ID_NOT_FOUND = (long) -1;
  // private final Long USER_ID_NOT_FOUND =  (long) -1;
  private static Integer FIRST_ORDER = 1;
  private final Integer BEGIN_INDEX = 7;
  Logger logger = LoggerFactory.getLogger(UserController.class);

  @Autowired JwtUtils jwtUtils;
  @Autowired UserService userService;
  @Autowired OrderService orderService;
  @Autowired ProductService productService;

  public List<OrderItem> getOrderItems(Long orderId) {
    List<OrderItem> orderItems = this.orderService.selectOrderItemsByOrderId(orderId);
    return orderItems;
  } 
  
  // Auto get the user_id based on the http request header
  @GetMapping("/orders/order_items/authenticated")
  @PreAuthorize("hasAuthority('USER')")
  public ResponseEntity<?>getOrderItemsByAccessToken(HttpServletRequest request) {
    logger.info("OrderController getOrderItemsByAccessToken method is running...");
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header not found");
    }
    HashMap<String, Object> map = new HashMap<String, Object>();
    String token = authHeader.substring(BEGIN_INDEX);
    System.out.println("token :>> " + token);
    String username = jwtUtils.extractUsername(token);
    System.out.println("username after extract from token :>> " + username);
    List<OrderItem> orderItems = orderService.selectOrderItemByUsername(username);
    logger.info("Order Items :>> ", orderItems);
    if (!orderItems.isEmpty()) {
      map.put("orderItems", orderItems);
      return new ResponseEntity<>(map, HttpStatus.OK);
    }
    map.put("orderItems", orderItems);
    return new ResponseEntity<>(map, HttpStatus.OK);
  }

  @GetMapping("/orders/order_items/{orderId}")
  @PreAuthorize("hasAuthority('USER')")
  public ResponseEntity<?>getOrderItemsByOrderId (@PathVariable Long orderId) {
    logger.info("OrderController getOrderItemsByOrderId method is running");
    HashMap<String, Object> map = new HashMap<String, Object>();
    List<OrderItem> orderItems = getOrderItems(orderId);
    map.put("orderItems", orderItems);
    return new ResponseEntity<>(map, HttpStatus.OK); 
  }

  @GetMapping("/orders/{userId}")
  @PreAuthorize("hasAuthority('USER')")
  public ResponseEntity<?>getOrder (@PathVariable String userId) {
    logger.info("OrderController getOrder method is running");
    HashMap<String, Object> map = new HashMap<String, Object>();
    Optional<Order> order = orderService.selectOrderUnpaidByUserId(userId);
    if (order.isEmpty()) {
      String warningMessage = "You do not have any order, please add product to your cart!";
      logger.warn(warningMessage);
      map.put("warningMessage", warningMessage);
      return new ResponseEntity<>(map, HttpStatus.OK); 
    } else {
      Order optionalOrder = order.get();
      // Get all order items
      if (order.isPresent())  {
        List<OrderItem> orderItems = getOrderItems(optionalOrder.getId());
        List<Product> products = new ArrayList<>();

        for (int i = 0; i < orderItems.size(); i++) {
          Long productId = orderItems.get(i).getProductId();
          System.out.println("product id :>> " + productId);
          Optional<Product> optionalProduct = this.productService.selectProductById(productId);
          if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            products.add(product);
          }
        }
        map.put("orderItems", orderItems);
        map.put("products", products);
      }
    } 
    String message = "Selected orders successfully";
    map.put("order", order);
    map.put("message", message);
    return new ResponseEntity<>(map, HttpStatus.OK);
  }

  @PutMapping("/orders/update")
  @PreAuthorize("hasAuthority('USER')")
  public ResponseEntity<?> updateOrderItem(@RequestBody @Valid OrderItem orderItem) {
    logger.info("OrderService updateOrderItem is running");
    int updateResult = orderService.updateQuantityOrderItem(orderItem);
    HashMap<String, Object> map = new HashMap<String, Object>();
    if (updateResult > 0) {
      logger.info("Update order item successfully");
      String message = "Update order item successfully";
      map.put("message", message);
      return new ResponseEntity<>(map, HttpStatus.OK);
    }
    String message = "Update order item unsuccessfully";
    map.put("message", message);
    return new ResponseEntity<>(map, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public boolean isOrderItemExist(Long orderId, Long productId) {
    int resultInsert = this.orderService.countNumberOfOrderItemByOrderIdAndProductId(orderId, productId);
    return resultInsert > 0 ? true : false;
  }

  @PostMapping("/orders/save")
  @PreAuthorize("hasAuthority('USER')")
  public ResponseEntity<?> saveOrder(@RequestBody @Valid Order order) {
    logger.info("OrderController saveOrder method is running...");
    HashMap<String, Object> map = new HashMap<String, Object>();
    logger.info("Product info :>> " + order.toString());

    // int resultInsertOrderItems = orderService.insertOrderItems(orderItems);

    // Insert order when payment_status = 'unpaid' and count = 1
    Integer numberOfOrders = orderService.countNumberOfOrderUnpaidByUserId(order.getUserId());
    System.out.println("orderService.countNumberOfOrderUnpaidByUserId(order.getUserId()):>> " + orderService.countNumberOfOrderUnpaidByUserId(order.getUserId()));
    if (numberOfOrders != FIRST_ORDER) {
      int resultInsertOrder = orderService.insertOrder(order);
      // Get user id -> based on user id -> get order id with payment_status is `unpaid`
      System.out.println("user id form frontend :>> " + order.getUserId());
      Optional<Order> selectedOrder = orderService.selectOrderUnpaidByUserId(order.getUserId());
      Long orderId = ORDER_ID_NOT_FOUND;

      if (selectedOrder.isPresent()) {
        Order orderFromDB = selectedOrder.get();
        orderId = orderFromDB.getId();
      }
      if (resultInsertOrder > 0) {
        logger.info("Insert Order successfully");
        String message = "Insert Order successfully";
        map.put("messageOne", message);
      }

      OrderItem orderItem = new OrderItem(orderId, order.getProductId(), order.getQuantity());
      Integer resultInsertOrderItems = orderService.insertOrderItems(orderItem);
      if (resultInsertOrderItems > 0) {
        logger.info("Insert OrderItems successfully");
        String message = "Insert OrderItems successfully";
        map.put("messageTwo", message);
      }

      // this.orderService.insertOrderItems(orderItems);
      // Update
    } else {
      logger.info("This order exists before");
      Optional<Order> selectedOrder = orderService.selectOrderUnpaidByUserId(order.getUserId());
      Long orderId = (long) -1;
      if (selectedOrder.isPresent()) {
        Order orderFromDB = selectedOrder.get();
        orderId = orderFromDB.getId();
      }

      order.setId(orderId);
      System.out.println("isOrderItemExist :>> " + isOrderItemExist(order.getId(), order.getProductId()));

      // If the product does not exists we will insert to the table sql
      if (!isOrderItemExist(order.getId(), order.getProductId())) {
        OrderItem orderItem = new OrderItem(order.getId(), order.getProductId(), order.getQuantity());
        Integer resultInsertOrderItems = orderService.insertOrderItems(orderItem);
        if (resultInsertOrderItems > 0) {
          logger.info("Insert OrderItems successfully");
          String message = "Insert OrderItems successfully";
          map.put("messageTwo", message);
        }

        return new ResponseEntity<>(map, HttpStatus.OK);
        // String message = "this product exists in the cart, please choose another one, thank you.";
        // map.put("errorMessage", message);
      } 
      else {
        String message = "This product exists in the cart, please choose another one, thank you.";
        logger.info("This product exists in the cart, please choose another one, thank you.");
        map.put("errorMessage", message);
        return new ResponseEntity<>(map, HttpStatus.CONFLICT);
      }
    } 

    return new ResponseEntity<>(map, HttpStatus.OK);
  }

  // Delete order item
  @PostMapping("/orders/delete")
  @PreAuthorize("hasAuthority('USER')")
  public ResponseEntity<?> deleteOrderItem(@RequestBody @Valid AuthOrderItem orderItem) { 
    logger.info("OrderController deleteOrderItem method is running");
    System.out.println("here");
    HashMap<String, Object> map = new HashMap<String, Object>();
    System.out.println("orderItem :>> " + orderItem.toString());
    int deleteResult = this.orderService.deleteOrderItem(orderItem);
    if (deleteResult > 0) {
      logger.info("Delete order item successfully");
      String message = "Delete order item successfully";
      map.put("message", message);
    } else {
      logger.info("Delete order item unsuccessfully");
      String message = "Delete order item unsuccessfully";
      map.put("message", message);
    }
    return new ResponseEntity<>(map, HttpStatus.OK);
  }
}
