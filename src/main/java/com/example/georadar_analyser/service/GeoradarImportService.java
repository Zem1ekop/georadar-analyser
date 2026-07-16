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

@Service
public class GeoradarImportService {

    private static final Logger log = LoggerFactory.getLogger(GeoradarImportService.class);
    private final TrackSectionRepository sectionRepository;

    // Автоматически внедряем поправку +0.40м из файла application.properties
    @Value("${georadar.geometry.axis-to-shoulder-correction:0.0}")
    private float axisCorrection;

    public GeoradarImportService(TrackSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    @Transactional

    public TrackSection importExcelData(InputStream inputStream, String lineName, Integer trackNumber, float axisCorrection) throws Exception {
        log.info("Запуск импорта реальных данных Geoscan32 для линии: {}", lineName);
        TrackSection section = new TrackSection();
        section.setLineName(lineName);
        section.setTrackNumber(trackNumber);
        section.setSurveyDate(java.time.LocalDate.now());

        // Кэш профилей. Ключом теперь является само физическое смещение Y из колонки B
        Map<Float, RadarProfile> profileCache = new HashMap<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
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

                    // 3. Карта слоев: Ключ — индекс колонки в Excel, Значение — имя слоя в системе
                    Map<Integer, String> columnLayers = Map.of(
                            2, "балласт",           // Колонка C
                            3, "песок",             // Колонка D
                            4, "земляное полотно"   // Колонка E
                    );

                    // Разбираем строку «матрицы» на отдельные точки слоев по столбцам
                    for (Map.Entry<Integer, String> entry : columnLayers.entrySet()) {
                        int columnIndex = entry.getKey();
                        String layerName = entry.getValue();

                        Cell cell = row.getCell(columnIndex);
                        if (cell != null && cell.getCellType() == CellType.NUMERIC) {

                            // Читаем абсолютную глубину из Geoscan32 (в см -> переводим в МЕТРЫ)
                            float rawDepthZ = (float) cell.getNumericCellValue() / 100.0f;

                            // НАША ИДЕАЛЬНАЯ ФОРМУЛА:
                            // Физический уровень Z = Высота поверхности профиля - Глубина залегания слоя
                            float correctedDepthZ = getSurfaceHeight(lateralOffsetY, axisCorrection) - rawDepthZ;


                            // Создаем и привязываем точку к профилю
                            LayerPoint point = new LayerPoint();
                            point.setDistanceX(distanceX);
                            point.setDepthZ(correctedDepthZ); // Сюда уйдет точное пространственное значение
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


