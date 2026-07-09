package com.example.georadar_analyser.repository;

import com.example.georadar_analyser.entity.LayerPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LayerPointRepository extends JpaRepository<LayerPoint, Long> {

    // Это нативный SQL-запрос для резюме.
    // Он находит точки, где глубина слоя превышает среднюю по профилю на величину 'threshold' (например, на 15 см).
    // Так мы автоматически локализуем просадочные корыта силами самой базы данных.
    @Query(value = "SELECT * FROM ( " +
            "  SELECT lp.*, AVG(lp.depth_z) OVER(PARTITION BY lp.profile_id, lp.layer_name) as avg_depth " +
            "  FROM layer_points lp " +
            "  JOIN radar_profiles rp ON lp.profile_id = rp.id " +
            "  WHERE rp.section_id = :sectionId AND lp.layer_name = :layerName " +
            ") sub WHERE sub.depth_z > (sub.avg_depth + :threshold)", nativeQuery = true)
    List<LayerPoint> findAnomalousDepressions(@Param("sectionId") Long sectionId,
                                              @Param("layerName") String layerName,
                                              @Param("threshold") Float threshold);
}

