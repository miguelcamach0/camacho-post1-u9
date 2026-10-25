
package com.clinica.recordatorios;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import com.clinica.recordatorios.service.CitaReminderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(CitaReminderServiceIntegrationTest.ClockDeterministaConfig.class)
class CitaReminderServiceIntegrationTest {

    @TestConfiguration
    static class ClockDeterministaConfig {

        @Bean
        @Primary
        public Clock clockDeterminista() {
            return Clock.fixed(
                Instant.parse("2026-08-10T09:00:00Z"),
                ZoneId.of("UTC")
            );
        }
    }

    @Autowired
    private CitaReminderService citaReminderService;

    @Autowired
    private CitaRepository citaRepository;

    @MockBean
    private NotificadorSmsGateway notificadorSmsGateway;

    @BeforeEach
    void setUp() {
        citaRepository.deleteAll();
    }

    @Test
    void enviarRecordatoriosPendientes_contextoCompletoDeSpring_persisteYNotificaCitasEnVentana() {

        Cita cita = citaRepository.save(
            new Cita(
                null,
                "Carla Ríos",
                "3011112222",
                LocalDateTime.of(2026, 8, 10, 18, 0),
                CanalNotificacion.SMS,
                false
            )
        );

        int enviados =
            citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, enviados);

        verify(notificadorSmsGateway, times(1))
            .enviarSms(
                eq("3011112222"),
                contains("Recordatorio")
            );

        Cita citaActualizada = citaRepository
            .findById(cita.getId())
            .orElseThrow();

        assertTrue(citaActualizada.isRecordatorioEnviado());
    }
}

