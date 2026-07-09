package com.example.georadar_analyser.service;

import com.example.georadar_analyser.entity.LayerPoint;
import com.example.georadar_analyser.entity.RadarProfile;
import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.repository.TrackSectionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service // Указывает Spring, что это сервисный слой с бизнес-логикой
public class GeoradarImportService {

    // Логгер для вывода профессиональных логов вместо устаревшего System.out.println
    private static final Logger log = LoggerFactory.getLogger(GeoradarImportService.class);

    private final TrackSectionRepository sectionRepository;

    // Внедряем репозиторий через конструктор (лучшая практика Spring)
    public GeoradarImportService(TrackSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    /**
     * Метод открывает Excel, парсит строки и сохраняет данные в БД.
     * @Transactional гарантирует, что если в середине файла произойдет ошибка,
     * база данных откатит изменения и в ней не останется "битых" кусков данных.
     */
    @Transactional
    public TrackSection importExcelData(InputStream inputStream, String lineName, Integer trackNumber) throws Exception {
        log.info("Запуск импорта данных георадара для ж/д линии: {}", lineName);

        // Создаем заголовочный объект участка пути
        TrackSection section = new TrackSection();
        section.setLineName(lineName);
        section.setTrackNumber(trackNumber);
        section.setSurveyDate(java.time.LocalDate.now());

        // Кэш для профилей, чтобы не плодить дубликаты (пр1 встретится в файле тысячи раз)
        Map<String, RadarProfile> profileCache = new HashMap<>();

        // Открываем книгу Excel (XSSFWorkbook читает современные файлы .xlsx)
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // Берем самый первый лист

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропускаем заголовок (строку 0)
                if (isRowEmpty(row)) continue;      // Пропускаем пустые строки

                try {
                    // Читаем ячейки: 0 - Метраж, 1 - Профиль, 2 - Глубина, 3 - Имя слоя
                    float distanceX = (float) row.getCell(0).getNumericCellValue();
                    String profileName = row.getCell(1).getStringCellValue();
                    float depthZ = (float) row.getCell(2).getNumericCellValue();
                    String layerName = row.getCell(3).getStringCellValue();

                    // Ищем профиль в кэше, если его еще не было — создаем и привязываем к участку
                    RadarProfile profile = profileCache.computeIfAbsent(profileName, name -> {
                        RadarProfile newProfile = new RadarProfile();
                        newProfile.setProfileName(name);
                        newProfile.setTrackSection(section);
                        newProfile.setLateralOffset(calculateOffset(name)); // Считаем смещение от оси
                        section.getProfiles().add(newProfile);
                        return newProfile;
                    });

                    // Создаем оцифрованную точку
                    LayerPoint point = new LayerPoint();
                    point.setDistanceX(distanceX);
                    point.setDepthZ(depthZ);
                    point.setLayerName(layerName);
                    point.setRadarProfile(profile);

                    profile.getPoints().add(point);

                } catch (Exception e) {
                    // Если одна строка "битая", логируем ошибку, но продолжаем читать файл дальше
                    log.warn("Ошибка парсинга строки {} в Excel: {}", row.getRowNum(), e.getMessage());
                }
            }
        }

        // Сохраняем корневой участок. Благодаря CascadeType.ALL, Hibernate сам
        // запишет в базу и профили, и все вложенные в них тысячи точек!
        TrackSection savedSection = sectionRepository.save(section);
        log.info("Импорт успешно завершен. Сохранено профилей: {}", savedSection.getProfiles().size());
        return savedSection;
    }

    private boolean isRowEmpty(Row row) {
        return row == null || row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK;
    }

    /**
     * Конвертируем текстовое имя профиля ("пр1", "пр2") в его физическое смещение в метрах от оси ж/д пути.
     */
    private Float calculateOffset(String profileName) {
        if (profileName == null) return 0.0f;
        String lowerName = profileName.toLowerCase().trim();

        switch (lowerName) {
            case "пр1": return -1.5f; // Левая сторона балластной призмы
            case "пр3": return 1.5f;  // Правая сторона балластной призмы
            case "пр2":               // Ось пути
            default: return 0.0f;
        }
    }
}

