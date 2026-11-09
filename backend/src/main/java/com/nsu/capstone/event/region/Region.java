package com.nsu.capstone.event.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @Column(length = 5)
    private String code;

    @Column(name = "province_code", nullable = false, length = 2)
    private String provinceCode;

    @Column(name = "province_name", nullable = false, length = 50)
    private String provinceName;

    @Column(nullable = false, length = 50)
    private String name;
}
