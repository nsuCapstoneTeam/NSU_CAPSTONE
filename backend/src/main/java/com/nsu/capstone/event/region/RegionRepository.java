package com.nsu.capstone.event.region;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, String> {

    List<Region> findAllByOrderByCodeAsc();
}
