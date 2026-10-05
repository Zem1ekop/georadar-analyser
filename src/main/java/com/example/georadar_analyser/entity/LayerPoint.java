package com.example.georadar_analyser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "layer_points", indexes = {
        // Индекс в БД для быстрого поиска конкретного слоя (например, "Балласт"),
        // когда точек станет очень много.
        @Index(name = "idx_layer_name", columnList = "layer_name")
})
@Getter
@Setter
public class LayerPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "layer_points_seq")
    @SequenceGenerator(
            name = "layer_points_seq",
            sequenceName = "layer_points_id_seq",
            allocationSize = 1000 // Выделяем сразу 1000 ID в память Java для сверхбыстрого импорта
    )
    private Long id;

    @Column(name = "distance_x", nullable = false)
    private Float distanceX;

    @Column(name = "depth_z", nullable = false)
    private Float depthZ;

    @Column(name = "layer_name", nullable = false)
    private String layerName;


    // Тысячи точек относятся к одному конкретному профилю (пр1, пр2...)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private RadarProfile radarProfile;
}
