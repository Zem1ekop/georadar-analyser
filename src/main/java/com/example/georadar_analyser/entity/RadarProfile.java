package com.example.georadar_analyser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "radar_profiles")
@Getter
@Setter
public class RadarProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_name", nullable = false)
    private String profileName; // Имя профиля (например, "пр1", "пр2")

    @Column(name = "lateral_offset")
    private Float lateralOffset; // Физическое смещение от оси пути в метрах

    // Множество профилей привязываются к одному ж/д участку.
    // FetchType.LAZY экономит память: данные участка не грузятся из БД, пока мы их не запросим.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private TrackSection trackSection;

    @OneToMany(mappedBy = "radarProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<LayerPoint> points = new java.util.LinkedHashSet<>();

}

