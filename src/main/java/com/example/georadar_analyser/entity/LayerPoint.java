package com.example.georadar_analyser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "layer_points", indexes = {
        // Индекс в БД для быстрого поиска конкретного слоя (например, "Балласт"),
        // когда точек станет очень много. Это сильный плюс для резюме.
        @Index(name = "idx_layer_name", columnList = "layer_name")
})
@Getter
@Setter
public class LayerPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "distance_x", nullable = false)
    private Float distanceX; // Строго с маленькой буквы d!

    @Column(name = "depth_z", nullable = false)
    private Float depthZ; // Строго с маленькой буквы d!

    @Column(name = "layer_name", nullable = false)
    private String layerName;


    // Тысячи точек относятся к одному конкретному профилю (пр1, пр2...)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private RadarProfile radarProfile;
}
