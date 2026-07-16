package com.example.georadar_analyser;

import com.example.georadar_analyser.repository.TrackSectionRepository;
import com.example.georadar_analyser.service.GeoradarImportService;
import com.example.georadar_analyser.view.Georadar3DViewer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class GeoradarAnalyserApplication implements CommandLineRunner {

    private final TrackSectionRepository sectionRepository;
    private final GeoradarImportService importService;
    private final Georadar3DViewer viewer;

    public GeoradarAnalyserApplication(TrackSectionRepository sectionRepository,
                                       GeoradarImportService importService,
                                       Georadar3DViewer viewer) {
        this.sectionRepository = sectionRepository;
        this.importService = importService;
        this.viewer = viewer;
    }

    public static void main(String[] args) {
        // Профессиональный трюк: отключаем встроенный веб-сервер для локального запуска графики 3D
        new SpringApplicationBuilder(GeoradarAnalyserApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        Georadar3DViewer.initDependencies(sectionRepository);

        System.out.println("=== Генерация бинарного тест-файла Excel (Стандарт Geoscan32) ===");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Профили");

            // ИМИТИРУЕМ СТРУКТУРУ GEOSCAN32:
            // Колонка 0: Метраж (см) | Колонка 1: Смещение (см) | ...колонки глубин слоев
            Object[][] data = {
                    {"см", "гориз смещ", "глубина", "глубина", "глубина"}, // Шапка таблицы

                    // ПРОФИЛЬ 1: Левое плечо балластной призмы (Y = 0 см)
                    {0.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {20.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {40.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {60.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {80.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {100.0f, 0.0f, 30.0f, 60.0f, 100.0f},

                    // ПРОФИЛЬ 2: Ось 1-го пути (Y = 150 см). Поверхность выше на 40 см!
                    {0.0f, 150.0f, 36.6f, 76.6f, 116.6f},   // <-- Абсолютная глубина 36.6 см (местная просадка в начале)
                    {20.0f, 150.0f, 35.1f, 75.1f, 115.1f},  // <--  Абсолютная глубина 35.1 см
                    {40.0f, 150.0f, 35.0f, 75.0f, 115.0f},
                    {60.0f, 150.0f, 35.0f, 75.0f, 115.0f},
                    {80.0f, 150.0f, 35.0f, 75.0f, 115.0f},
                    {100.0f, 150.0f, 35.0f, 75.0f, 115.0f},

                    // ПРОФИЛЬ 3: Междупутье (Y = 350 см). Поверхность выше плеча на 20 см.
                    {0.0f, 350.0f, 30.0f, 65.0f, 105.0f},
                    {20.0f, 350.0f, 30.0f, 65.0f, 105.0f},
                    {40.0f, 350.0f, 30.0f, 65.0f, 105.0f},
                    {60.0f, 350.0f, 30.0f, 65.0f, 105.0f},
                    {80.0f, 350.0f, 30.0f, 65.0f, 105.0f},
                    {100.0f, 350.0f, 30.0f, 65.0f, 105.0f}
            };

            int rowCount = 0;
            for (Object[] dataRow : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowCount++);
                int columnCount = 0;
                for (Object field : dataRow) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(columnCount++);
                    if (field instanceof String) {
                        cell.setCellValue((String) field);
                    } else if (field instanceof Float) {
                        cell.setCellValue((Float) field);
                    }
                }
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            byte[] excelBytes = bos.toByteArray();
            java.io.ByteArrayInputStream realExcelStream = new java.io.ByteArrayInputStream(excelBytes);

            System.out.println("=== Инициализация текстовых данных георадара ===");
            var savedSection = importService.importExcelData(realExcelStream, "Тестовый перегон Geoscan32", 1);

            System.out.println("=== Запуск 3D Визуализатора JavaFX ===");
            viewer.viewSection(savedSection.getId());
        }
    }

}


