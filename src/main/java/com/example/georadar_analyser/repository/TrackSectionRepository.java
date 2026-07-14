package com.example.georadar_analyser.repository;

import com.example.georadar_analyser.entity.TrackSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackSectionRepository extends JpaRepository<TrackSection, Long> {

    // Гениальный SQL-запрос для резюме: JOIN FETCH склеивает таблицы при выборке,
    // выкачивая дерево "Участок -> Профили -> Точки" за 1 эффективный запрос к БД,
    // полностью предотвращая LazyInitializationException.
    @Query("SELECT ts FROM TrackSection ts " +
            "LEFT JOIN FETCH ts.profiles p " +
            "LEFT JOIN FETCH p.points " +
            "WHERE ts.id = :id")
    Optional<TrackSection> findByIdWithWithAllData(@Param("id") Long id);
}
