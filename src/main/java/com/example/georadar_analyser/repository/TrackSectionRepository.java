package com.example.georadar_analyser.repository;

import com.example.georadar_analyser.entity.TrackSection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TrackSectionRepository extends JpaRepository<TrackSection, Long> {

    /**
     * Эффективная загрузка полного графа данных "Участок -> Профили -> Точки"
     * за минимальное количество запросов без генерации декартова произведения (Cartesian Product).
     */
    @EntityGraph(attributePaths = {"profiles", "profiles.points"})
    Optional<TrackSection> findById(Long id);
}
