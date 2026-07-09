package com.example.georadar_analyser.repository;

import com.example.georadar_analyser.entity.TrackSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository // Указывает Spring Boot, что этот компонент отвечает за доступ к БД
public interface TrackSectionRepository extends JpaRepository<TrackSection, Long> {
    // Наследуя JpaRepository, мы бесплатно получаем готовые методы: save(), findById(), delete()
}
