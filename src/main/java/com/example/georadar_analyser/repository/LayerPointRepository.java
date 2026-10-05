package com.example.georadar_analyser.repository;

import com.example.georadar_analyser.entity.LayerPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LayerPointRepository extends JpaRepository<LayerPoint, Long> {

    /**
     * ВЫЯВЛЕНИЕ ЛОКАЛЬНЫХ ДЕФОРМАЦИЙ (ПРОСАДОЧНЫХ КОРЫТ) МЕТОДОМ СКОЛЬЗЯЩЕГО ОКНА
     * Оптимизировано под индексы PostgreSQL. Фильтрация по профилям выполняется ДО расчета оконной функции.
     */
    @Query(value = """
        SELECT id, distance_x, depth_z, layer_name, profile_id 
        FROM (
            SELECT lp.*, 
                   AVG(lp.depth_z) OVER(
                       PARTITION BY lp.profile_id, lp.layer_name 
                       ORDER BY lp.distance_x
                       ROWS BETWEEN 10 PRECEDING AND 10 FOLLOWING
                   ) as avg_depth
            FROM layer_points lp
            WHERE lp.profile_id IN (
                SELECT rp.id FROM radar_profiles rp WHERE rp.section_id = :sectionId
            ) AND lp.layer_name = :layerName
        ) sub 
        WHERE sub.depth_z > (sub.avg_depth + :threshold)
        """, nativeQuery = true)
    List<LayerPoint> findAnomalousDepressions(@Param("sectionId") Long sectionId,
                                              @Param("layerName") String layerName,
                                              @Param("threshold") Float threshold);
}
