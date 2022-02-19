package com.thoughtworks.archgard.scanner.controller

import com.thoughtworks.archgard.scanner.domain.config.dto.ConfigureDTO
import com.thoughtworks.archgard.scanner.domain.config.dto.UpdateDTO
import com.thoughtworks.archgard.scanner.domain.config.dto.UpdateMessageDTO
import com.thoughtworks.archgard.scanner.domain.config.service.ConfigureService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/")
class ConfigureController {

    @Autowired
    private lateinit var configureService: ConfigureService


    @GetMapping("/config")
    fun getConfigures(): List<ConfigureDTO> {
        return configureService.getConfigures()
    }

    @PostMapping("/config")
    fun updateConfigure(@RequestBody configs: List<UpdateDTO>): UpdateMessageDTO {
        return configureService.updateConfigure(configs)
    }
}