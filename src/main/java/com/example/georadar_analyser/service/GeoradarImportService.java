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
    private final GeometryService geometryService;

    public GeoradarImportService(TrackSectionRepository sectionRepository, GeometryService geometryService) {
        this.sectionRepository = sectionRepository;
        this.geometryService = geometryService;
    }

    /**
     * ПАКЕТНЫЙ ИМПОРТ ДАННЫХ GEOSCAN32 С АВТОМАТИЧЕСКОЙ ГЕОДЕЗИЧЕСКОЙ ПРИВЯЗКОЙ
     */
    @Transactional
    public TrackSection importExcelData(InputStream inputStream, String lineName, Integer trackNumber, Long existingSectionId) throws Exception {
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
                    float lateralOffsetY = geometryService.normalizeLateralOffset((float) row.getCell(1).getNumericCellValue() / 100.0f);

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

                            // Физический уровень Z
                            float correctedDepthZ = geometryService.getProjectHeight(lateralOffsetY) - rawDepthZ;

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
}
