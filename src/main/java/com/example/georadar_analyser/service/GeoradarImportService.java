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
     * CAD-МЕТОД КУСОЧНО-ЛИНЕЙНОЙ ИНТЕРПОЛЯЦИИ ПОВЕРХНОСТИ БАЛЛАСТНОЙ ПРИЗМЫ
     * Рассчитывает плавные подъёмы, откосы и горизонтальные подшпальные площадки.
     * @param y - текущее смещение от левого плеча в метрах (0.0м - наш базовый ноль)
     * @param axisCorrection - высота балластной призмы под рельсами (например, +0.40м)
     */
    private float getSurfaceHeight(float y, float axisCorrection) {
        float hMax = axisCorrection;      // Высота подшпальной колыбели (+0.40м)
        float hMid = axisCorrection / 2f; // Высота междупутья (+0.20м)
        float hMin = 0.0f;                // Высота плеч и обочин (0.0м)

        // ЗОНА 1: Слева от левого плеча (Обочина земляного полотна)
        if (y < 0.0f) {
            return hMin; // Абсолютно ровный горизонтальный ноль обочины
        }
        // ЗОНА 2: Внешний откос балластной призмы 1-го пути (Подъём от плеча к шпалам)
        else if (y >= 0.0f && y < 0.4f) {
            float ratio = (y - 0.0f) / (0.4f - 0.0f);
            return hMin + ratio * (hMax - hMin); // Плавно растёт от 0.0м до +0.40м
        }
        // ЗОНА 3: Горизонтальная подшпальная площадка 1-го пути (Включает ось 1.5м)
        else if (y >= 0.4f && y < 2.6f) {
            return hMax; // Ровная плоскость +0.40м, никаких торчащих пиков!
        }
        // ЗОНА 4: Внутренний откос призмы 1-го пути (Спуск к междупутью)
        else if (y >= 2.6f && y < 3.0f) {
            float ratio = (y - 2.6f) / (3.0f - 2.6f);
            return hMax + ratio * (hMid - hMax); // Плавно падает от +0.40м до +0.20м
        }
        // ЗОНА 5: Горизонтальное междупутье
        else if (y >= 3.0f && y < 4.0f) {
            return hMid; // Ровная плоскость разделения путей +0.20м
        }
        // ЗОНА 6: Внутренний откос призмы 2-го пути (Подъём от междупутья к шпалам)
        else if (y >= 4.0f && y < 4.4f) {
            float ratio = (y - 4.0f) / (4.4f - 4.0f);
            return hMid + ratio * (hMax - hMid); // Плавно растёт от +0.20м до +0.40м
        }
        // ЗОНА 7: Горизонтальная подшпальная площадка 2-го пути (Включает ось 5.5м)
        else if (y >= 4.4f && y < 6.6f) {
            return hMax; // Ровная плоскость +0.40м под вторым путем
        }
        // ЗОНА 8: Внешний откос балластной призмы 2-го пути (Спуск к правому плечу)
        else if (y >= 6.6f && y < 7.0f) {
            float ratio = (y - 6.6f) / (7.0f - 6.6f);
            return hMax + ratio * (hMin - hMax); // Плавно падает от +0.40м до 0.0м
        }
        // ЗОНА 9: Правее правого плеча (Правая обочина)
        else {
            return hMin; // Возвращаемся в ноль
        }
    }

}
