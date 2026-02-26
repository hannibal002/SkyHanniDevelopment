package skyhanni.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import skyhanni.plugin.MyBundle

@Service(Service.Level.PROJECT)
class SkyHanniPluginService(project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
    }
}
