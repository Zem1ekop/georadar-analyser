package com.example.georadar_analyser.service;

import com.example.georadar_analyser.entity.LayerPoint;
import com.example.georadar_analyser.entity.RadarProfile;
import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.repository.TrackSectionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.WorkbookFactory;


@Service
public class GeoradarImportService {

    private static final Logger log = LoggerFactory.getLogger(GeoradarImportService.class);
    private final TrackSectionRepository sectionRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    // Автоматически внедряем поправку +0.40м из файла application.properties
    @Value("${georadar.geometry.axis-to-shoulder-correction:0.0}")
    private float axisCorrection;

    public GeoradarImportService(TrackSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    @Transactional
    // Добавляем параметр Long existingSectionId в сигнатуру метода
    public TrackSection importExcelData(InputStream inputStream, String lineName, Integer trackNumber, float axisCorrection, Long existingSectionId) throws Exception {
        log.info("Запуск импорта данных Geoscan32 для линии: {}", lineName);

        TrackSection section;
        // Если ID передан — достаем существующий участок из базы, если null — создаем новый!
        if (existingSectionId != null) {
            section = sectionRepository.findById(existingSectionId).orElse(new TrackSection());
        } else {
            section = new TrackSection();
            section.setLineName(lineName);
            section.setTrackNumber(trackNumber);
            section.setSurveyDate(java.time.LocalDate.now());
        }

        // Кэш профилей. Ключом теперь является само физическое смещение Y из колонки B
        Map<Float, RadarProfile> profileCache = new HashMap<>();

        // Парсер сам поймет формат файла (.xls или .xlsx)
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропускаем шапку таблицы
                if (isRowEmpty(row)) continue;

                try {
                    // 1. Читаем координату X (Колонка А: см -> переводим в МЕТРЫ)
                    float distanceX = (float) row.getCell(0).getNumericCellValue() / 100.0f;

                    // 2. Читаем смещение Y (Колонка B: см -> переводим в МЕТРЫ)
                    float lateralOffsetY = (float) row.getCell(1).getNumericCellValue() / 100.0f;

                    // Получаем из кэша или создаем профиль на основе его смещения Y
                    RadarProfile profile = profileCache.computeIfAbsent(lateralOffsetY, offset -> {
                        RadarProfile newProfile = new RadarProfile();
                        newProfile.setProfileName("Профиль Y=" + offset + "м");
                        newProfile.setTrackSection(section);
                        newProfile.setLateralOffset(offset);
                        section.getProfiles().add(newProfile);
                        return newProfile;
                    });

                    // Читаем все доступные колонки со слоями, начиная с колонки C (индекс 2)
                    int lastColumnIndex = row.getLastCellNum();
                    for (int columnIndex = 2; columnIndex < lastColumnIndex; columnIndex++) {

                        Cell cell = row.getCell(columnIndex);
                        if (cell != null && cell.getCellType() == CellType.NUMERIC) {

                            // Автоматически даем имя по номеру границы ("Слой 1", "Слой 2"...)
                            String layerName = "Слой " + (columnIndex - 1);

                            // Читаем абсолютную глубину (в см -> переводим в МЕТРЫ)
                            float rawDepthZ = (float) cell.getNumericCellValue() / 100.0f;

                            // Физический уровень Z = Высота поверхности - Глубина
                            float correctedDepthZ = getSurfaceHeight(lateralOffsetY, axisCorrection) - rawDepthZ;

                            LayerPoint point = new LayerPoint();
                            point.setDistanceX(distanceX);
                            point.setDepthZ(correctedDepthZ);
                            point.setLayerName(layerName);
                            point.setRadarProfile(profile);

                            profile.getPoints().add(point);
                        }
                    }

                } catch (Exception e) {
                    log.warn("Ошибка парсинга строки {} в Excel: {}", row.getRowNum(), e.getMessage());
                }
            }
        }

        TrackSection savedSection = sectionRepository.save(section);

        // ХАК ДЛЯ JAVAFX: Принудительно выталкиваем данные из кэша Hibernate прямо в таблицы H2!
        entityManager.flush();

        log.info("Импорт Geoscan32 успешно завершен. Создано профилей: {}", savedSection.getProfiles().size());

        return savedSection;
    }

    private boolean isRowEmpty(Row row) {
        return row == null || row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK;
    }

    /**
     * МЕТОД ОПРЕДЕЛЕНИЯ ВЫСОТЫ ДНЕВНОЙ ПОВЕРХНОСТИ ПРОФИЛЯ
     * @param lateralOffset - смещение профиля в метрах
     * @return абсолютная высота поверхности относительно плеча (в метрах)
     */
    /**
     * МЕТОД ОПРЕДЕЛЕНИЯ ВЫСОТЫ ДНЕВНОЙ ПОВЕРХНОСТИ ПРОФИЛЯ (Динамический)
     * @param lateralOffset - смещение профиля в метрах
     * @param axisCorrection - поправка, прилетевшая из текстового поля GUI
     * @return абсолютная высота поверхности относительно плеча (в метрах)
     */
    // Обновленный метод в самом низу класса:
    private float getSurfaceHeight(float lateralOffset, float axisCorrection) {
        if (Math.abs(lateralOffset - 1.5f) < 0.1f || Math.abs(lateralOffset - 5.5f) < 0.1f) {
            return axisCorrection; // Используем поправку, прилетевшую с экрана!
        }
        if (Math.abs(lateralOffset - 3.5f) < 0.1f) {
            return axisCorrection / 2f;
        }
        return 0.0f;
    }


}


