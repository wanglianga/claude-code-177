package com.community.water.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 维护师傅 */
@Getter
@Setter
@Entity
@Table(name = "technician")
public class Technician {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String techNo;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 32)
    private String phone;

    /** AVAILABLE / BUSY / OFF_DUTY */
    @Column(nullable = false, length = 32)
    private String status = "AVAILABLE";
}
