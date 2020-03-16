package com.thoughtworks.archguard.project_info.domain.service

import com.thoughtworks.archguard.project_info.domain.dto.ProjectInfoDTO
import com.thoughtworks.archguard.project_info.domain.dto.ProjectInfoUpdateMessage
import com.thoughtworks.archguard.project_info.domain.repository.ProjectInfoRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ProjectInfoService {

    @Autowired
    lateinit var projectInfoRepository: ProjectInfoRepository

    fun getProjectInfo(): ProjectInfoDTO = projectInfoRepository.getProjectInfo()

    fun updateProjectInfo(projectInfo: ProjectInfoDTO) =
            if (projectInfoRepository.updateProjectInfo(projectInfo) == 1) {
                ProjectInfoUpdateMessage(true, "update project info success")
            } else {
                ProjectInfoUpdateMessage(false, "update error")
            }

}