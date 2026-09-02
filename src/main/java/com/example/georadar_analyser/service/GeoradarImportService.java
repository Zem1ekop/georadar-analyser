package com.example.georadar_analyser.service;

import com.example.georadar_analyser.entity.LayerPoint;
import com.example.georadar_analyser.entity.RadarProfile;
import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.repository.TrackSectionRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class GeoradarImportService {

    private static final Logger log = LoggerFactory.getLogger(GeoradarImportService.class);
    private final TrackSectionRepository sectionRepository;

    public GeoradarImportService(TrackSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    /**
     * ПАКЕТНЫЙ ИМПОРТ ДАННЫХ GEOSCAN32 С УЧЕТОМ ДИНАМИЧЕСКИХ КОРРЕКЦИЙ
     */
    @Transactional
    public TrackSection importExcelData(InputStream inputStream, String lineName, Integer trackNumber, float axisCorrection, Long existingSectionId) throws Exception {
        log.info("Запуск импорта данных Geoscan32 для линии: {}", lineName);

        TrackSection section;
        if (existingSectionId != null) {
            section = sectionRepository.findById(existingSectionId).orElse(new TrackSection());
        } else {
            section = new TrackSection();
            section.setLineName(lineName);
            section.setTrackNumber(trackNumber);
            section.setSurveyDate(java.time.LocalDate.now());
        }

        Map<Float, RadarProfile> profileCache = new HashMap<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (isRowEmpty(row)) continue;

                try {
                    float distanceX = (float) row.getCell(0).getNumericCellValue() / 100.0f;
                    float lateralOffsetY = (float) row.getCell(1).getNumericCellValue() / 100.0f;

                    // --- ГЕОДЕЗИЧЕСКИЙ АДАПТЕР ПРИВЯЗКИ ПРОФИЛЕЙ К СТРОИТЕЛЬНОМУ ШАБЛОНУ ---
                    if (Math.abs(lateralOffsetY - 0.0f) < 0.1f) {
                        lateralOffsetY = 0.000f;  // Профиль 1: Левое плечо 1-го пути мёртво в ноль
                    } else if (Math.abs(lateralOffsetY - 1.5f) < 0.1f) {
                        lateralOffsetY = 1.775f;  // Профиль 2: Ось 1-го пути СТРОГО ПО ЦЕНТРУ шпалы
                    } else if (Math.abs(lateralOffsetY - 3.5f) < 0.1f) {
                        lateralOffsetY = 4.7125f; // Профиль 3: Междупутье чётко по центру плоской трапеции!
                    } else if (Math.abs(lateralOffsetY - 5.5f) < 0.1f) {
                        lateralOffsetY = 7.650f;  // Профиль 4: Ось 2-го пути СТРОГО ПО ЦЕНТРУ второй шпалы
                    } else if (Math.abs(lateralOffsetY - 7.0f) < 0.1f) {
                        lateralOffsetY = 9.425f;  // Профиль 5: Правое плечо 2-го пути ложится на бровку балласта
                    }



                    RadarProfile profile = profileCache.computeIfAbsent(lateralOffsetY, offset -> {
                        RadarProfile newProfile = new RadarProfile();
                        newProfile.setProfileName("Профиль Y=" + offset + "м");
                        newProfile.setTrackSection(section);
                        newProfile.setLateralOffset(offset);
                        section.getProfiles().add(newProfile);
                        return newProfile;
                    });

                    int lastColumnIndex = row.getLastCellNum();
                    for (int columnIndex = 2; columnIndex < lastColumnIndex; columnIndex++) {
                        Cell cell = row.getCell(columnIndex);

                        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                            String layerName = "Слой " + (columnIndex - 1);
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
        log.info("Импорт Geoscan32 успешно завершен. Создано профилей: {}", savedSection.getProfiles().size());
        return savedSection;
    }

    private boolean isRowEmpty(Row row) {
        return row == null || row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK;
    }

    /**
     * СИНХРОНИЗИРОВАННЫЙ СТРОИТЕЛЬНЫЙ ШАБЛОН ПОВЕРХНОСТИ ДЛЯ ПАРСЕРА
     * Защищает слои грунта от выпирания на междупутье!
     */
    private float getSurfaceHeight(float lateralOffset, float axisCorrection) {
        float hBallastTop   = axisCorrection; // Высота призмы путей (например, +0.40м)
        float hSubgradeZero = 0.0f;           // Уровень обочин и междупутья (0.0м)

        // Ориентируемся по условным координатам изначального Excel-файла
        if (Math.abs(lateralOffset - 0.0f) < 0.1f) {
            return hBallastTop; // Профиль 1 (Левое плечо 1 пути) — на верху призмы!
        }
        else if (Math.abs(lateralOffset - 1.5f) < 0.1f) {
            return hBallastTop; // Профиль 2 (Ось 1 пути) — на верху призмы
        }
        else if (Math.abs(lateralOffset - 3.5f) < 0.1f) {
            // СТАЛО: Междупутье в Excel равно 3.5м. Оно лежит на уровне земполотна!
            return hSubgradeZero; // Возвращаем честный плоский ноль 0.0м!
        }
        else if (Math.abs(lateralOffset - 5.5f) < 0.1f) {
            return hBallastTop; // Профиль 4 (Ось 2 пути) — на верху призмы
        }
        else if (Math.abs(lateralOffset - 7.0f) < 0.1f) {
            return hBallastTop; // Профиль 5 (Правое плечо 2 пути) — на верху призмы
        }

        return hSubgradeZero;
    }
}
