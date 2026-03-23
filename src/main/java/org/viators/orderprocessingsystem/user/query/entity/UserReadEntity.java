package org.viators.orderprocessingsystem.user.query.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.viators.orderprocessingsystem.common.BaseReadEntity;
import org.viators.orderprocessingsystem.common.enums.UserRolesEnum;

@Entity
@Table(name = "users_read_view")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserReadEntity extends BaseReadEntity {

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "firstname")
    private String firstName;

    @Column(name = "lastname")
    private String lastName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    @Builder.Default
    private UserRolesEnum userRole = UserRolesEnum.CUSTOMER;

    @Column(name = "total_orders_placed")
    private Long totalOrdersPlaced;
}
