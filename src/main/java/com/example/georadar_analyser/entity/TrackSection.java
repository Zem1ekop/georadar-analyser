package com.example.georadar_analyser.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity // Указывает Hibernate, что этот класс станет таблицей в базе данных
@Table(name = "track_sections") // Задает физическое имя таблицы в БД
@Getter // Автоматически создает геттеры (благодаря библиотеке Lombok)
@Setter // Автоматически создает сеттеры (благодаря библиотеке Lombok)
public class TrackSection {

    @Id // Указывает, что это первичный ключ (уникальный ID строки)
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Включает автонумерацию (1, 2, 3...)
    private Long id;

    @Column(name = "line_name", nullable = false)
    private String lineName; // Название ж/д направления (например, "Москва-Рязань")

    @Column(name = "track_number")
    private Integer trackNumber; // Номер главного пути (1, 2, 3...)

    @Column(name = "survey_date")
    private LocalDate surveyDate; // Дата проведения георадиолокации

    // CascadeType.ALL означает: если мы удалим участок пути, из БД автоматически удалятся и все его профили
    @OneToMany(mappedBy = "trackSection", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<RadarProfile> profiles = new java.util.LinkedHashSet<>();

}

