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
        System.out.println("=== Генерация бинарного тест-файла Excel (Стандарт Geoscan32) ===");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Профили");

            // ИМИТИРУЕМ СЛОЖНЫЙ ГЕОФИЗИЧЕСКИЙ ЭКСПОРТ (3 профиля с выклиниванием нижнего слоя)
            // Колонка 0: Метраж (см) | Колонка 1: Смещение (см) | К2: Балласт | К3: Песок | К4: Земляное полотно
            Object[][] data = {
                    {"см", "гориз смещ", "глубина", "глубина", "глубина"}, // Шапка таблицы

                    // ПРОФИЛЬ 1: Левое плечо балластной призмы (Смещение Y = 0 см) — есть ВСЕ 3 слоя
                    {0.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {20.0f, 0.0f, 30.0f, 60.0f, 100.0f},
                    {40.0f, 0.0f, 30.0f, 60.0f, 100.0f},

                    // ПРОФИЛЬ 2: Ось пути (Смещение Y = 150 см)
                    // ВНИМАНИЕ: Антенна не пробила нижний слой! Последняя колонка (индекс 4) равна null!
                    {0.0f, 150.0f, 35.0f, 75.0f, null}, // <-- Нет земляного полотна!
                    {20.0f, 150.0f, 35.0f, 75.0f, null}, // <-- Нет земляного полотна!
                    {40.0f, 150.0f, 35.0f, 75.0f, null}, // <-- Нет земляного полотна!

                    // ПРОФИЛЬ 3: Правое плечо балластной призмы (Смещение Y = 300 см) — снова есть ВСЕ 3 слоя
                    {0.0f, 300.0f, 30.0f, 60.0f, 100.0f},
                    {20.0f, 300.0f, 30.0f, 60.0f, 100.0f},
                    {40.0f, 300.0f, 30.0f, 60.0f, 100.0f}
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
                    // Если поле null (как на оси пути), мы просто оставляем ячейку пустой,
                    // точно так же, как это делает софт Geoscan32 в реальности!
                }
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            byte[] excelBytes = bos.toByteArray();
            java.io.ByteArrayInputStream realExcelStream = new java.io.ByteArrayInputStream(excelBytes);

            System.out.println("=== Инициализация текстовых данных георадара ===");
            var savedSection = importService.importExcelData(realExcelStream, "Тестовый перегон Geoscan32", 1, null);



            System.out.println("=== Запуск 3D Визуализатора JavaFX ===");
            // Передаем ID участка и ссылки на наши Spring-сервисы напрямую!
            viewer.viewSection(savedSection.getId(), sectionRepository, importService);

        }
    }
}


