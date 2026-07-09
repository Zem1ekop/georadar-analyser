package com.example.georadar_analyser.repository;

import com.example.georadar_analyser.entity.RadarProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RadarProfileRepository extends JpaRepository<RadarProfile, Long> {
}

