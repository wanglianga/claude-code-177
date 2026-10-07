package com.community.water.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "technicians")
@Getter
@Setter
public class Technician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    /** 技能片区，按小区近似匹配 */
    private Long communityId;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
