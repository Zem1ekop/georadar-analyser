package com.example.georadar_analyser;

import com.example.georadar_analyser.repository.TrackSectionRepository;
import com.example.georadar_analyser.service.GeoradarImportService;
import com.example.georadar_analyser.view.Georadar3DViewer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class GeoradarAnalyserApplication implements CommandLineRunner {

    private final TrackSectionRepository sectionRepository;
    private final GeoradarImportService importService;
    private final Georadar3DViewer viewer;

    // Внедряем все наши слои в главный класс приложения
    public GeoradarAnalyserApplication(TrackSectionRepository sectionRepository,
                                       GeoradarImportService importService,
                                       Georadar3DViewer viewer) {
        this.sectionRepository = sectionRepository;
        this.importService = importService;
        this.viewer = viewer;
    }

    public static void main(String[] args) {
        SpringApplication.run(GeoradarAnalyserApplication.class, args);
    }

    /**
     * Этот метод автоматически выполнится СРАЗУ после старта Spring Boot.
     * Здесь мы сымитируем загрузку тестовых данных и откроем наше 3D-окно.
     */
    @Override
    public void run(String... args) throws Exception {
        // Передаем репозиторий в статический контекст JavaFX
        Georadar3DViewer.initDependencies(sectionRepository);

        System.out.println("=== Инициализация тестовых данных георадара ===");

        // Создаем имитацию Excel-файла (CSV-поток) для теста нашего парсера.
        // Метраж (X) | Профиль (Y) | Глубина (Z) | Имя слоя
        String csvData = "Метраж,Профиль,Глубина,Слой\n" +
                "10.0,пр1,0.30,балласт\n" +
                "10.0,пр1,0.60,песок\n" +
                "11.0,пр1,0.35,балласт\n" +
                "11.0,пр1,0.65,песок\n" +
                "12.0,пр1,0.55,балласт\n" + // <-- Имитируем просадку балласта (уход глубины с 0.35 до 0.55м)
                "12.0,пр1,0.70,песок\n" +

                "10.0,пр2,0.32,балласт\n" +
                "10.0,пр2,0.62,песок\n" +
                "11.0,пр2,0.34,балласт\n" +
                "11.0,пр2,0.64,песок\n" +
                "12.0,пр2,0.58,балласт\n" + // <-- Просадка по оси пути
                "12.0,пр2,0.72,песок\n";

        ByteArrayInputStream fakeExcelStream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        // Тестируем всю нашу цепочку: парсим поток и сохраняем в H2 базу данных
        var savedSection = importService.importExcelData(fakeExcelStream, "Перегон Москва-Рязань, ПК45", 2);

        System.out.println("=== Запуск 3D Визуализатора JavaFX ===");
        // Открываем графическое 3D-окно для сохраненного участка!
        viewer.viewSection(savedSection.getId());
    }
}

