package com.example.shop_pet.models;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
  @NotNull(message = "orderId shouldn't be null")
  private Long orderId;

  @NotNull(message = "productId shouldn't be null")
  private Long productId;

  @Min(1)
  @Max(9)
  private int quantity;

  public OrderItem(Long orderId, Long productId, int quantity) {
    this.orderId = orderId;
    this.productId = productId;
    this.quantity = quantity;
  }
}
