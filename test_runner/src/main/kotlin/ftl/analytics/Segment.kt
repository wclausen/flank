package ftl.analytics

import com.segment.analytics.Analytics
import com.segment.analytics.messages.TrackMessage

object Segment {
    // private val analytics: Analytics = Analytics.builder("3yu0CohL2MdKr1M2iyCOzsHiJBA9NUOB")
    private val analytics: Analytics = Analytics.builder("1oFcTHdHw6EGgMucXvdwBEzR531DGF1m")
        .build()

    private var userId = ""

    fun identifyUser(projectId: String) {
        userId = projectId
    }

    fun logConfiguration(configurationProperties: Map<String, Any?>) {
        analytics.enqueue(
            TrackMessage.builder("Configuriation")
                .userId(userId)
                .properties(configurationProperties)
        )
        analytics.flush()
    }
}
