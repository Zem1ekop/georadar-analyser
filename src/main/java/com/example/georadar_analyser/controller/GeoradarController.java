package com.example.georadar_analyser.controller;

import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.service.GeoradarImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController // Говорит Spring, что этот класс обрабатывает веб-запросы и возвращает данные
@RequestMapping("/api/v1/georadar") // Задает базовый сетевой адрес для всех запросов в этом классе
public class GeoradarController {

    private static final Logger log = LoggerFactory.getLogger(GeoradarController.class);
    private final GeoradarImportService importService;

    // Внедряем наш сервис парсинга через конструктор (Dependency Injection)
    public GeoradarController(GeoradarImportService importService) {
        this.importService = importService;
    }

    /**
     * Сетевой эндпоинт для загрузки файла радарограмм.
     * Принимает файл (MultipartFile) и текстовые параметры из HTTP-запроса.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadGeoradarFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lineName") String lineName,
            @RequestParam("trackNumber") Integer trackNumber) {

        log.info("Получен веб-запрос на импорт файла: {}, для линии: {}", file.getOriginalFilename(), lineName);

        // Быстрая валидация: если файл забыли прикрепить — сразу возвращаем ошибку клиента (400 Bad Request)
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Ошибка: файл пуст или отсутствует.");
        }

        try {
            // Передаем поток данных загруженного файла прямо в наш сервис
            TrackSection savedSection = importService.importExcelData(
                    file.getInputStream(),
                    lineName,
                    trackNumber
            );

            // Если всё прошло успешно, возвращаем статус 201 (Created) и ID новой записи в БД
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Данные успешно импортированы в БД. ID участка: " + savedSection.getId());

        } catch (Exception e) {
            log.error("Критическая ошибка при обработке файла контроллером: ", e);
            // Если что-то сломалось внутри парсера, сервер честно ответит ошибкой 500 (Internal Server Error)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка на стороне сервера при обработке файла: " + e.getMessage());
        }
    }
}

