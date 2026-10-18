package com.nsu.capstone.event.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regions")
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

    protected Region() {
    }

    public String getCode() {
        return code;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public String getName() {
        return name;
    }
}
