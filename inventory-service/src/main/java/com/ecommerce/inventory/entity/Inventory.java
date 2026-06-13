package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer totalStock;

    @Column(nullable = false)
    @Builder.Default
    private Integer reservedStock = 0;

    @Version
    private Long version;

    public Integer getAvailableStock() {
        return totalStock - reservedStock;
    }
}
