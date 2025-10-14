package lg.intellij.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import lg.intellij.cli.CliResolver

/**
 * Listener for Settings changes.
 * 
 * Invalidates CLI resolver cache when Settings change to ensure
 * the new configuration is picked up on next CLI invocation.
 */
interface LgSettingsChangeListener {
    
    /**
     * Called when Settings are applied/changed.
     */
    fun settingsChanged()
    
    companion object {
        /**
         * Message Bus topic for Settings changes.
         */
        val TOPIC = Topic.create(
            "LG Settings Changed",
            LgSettingsChangeListener::class.java
        )
        
        /**
         * Notifies all listeners about Settings changes.
         * 
         * Should be called from Configurable.apply().
         */
        fun notifySettingsChanged() {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(TOPIC)
                .settingsChanged()
        }
    }
}

/**
 * Default listener that invalidates CLI resolver cache on Settings change.
 */
class CliResolverCacheInvalidator : LgSettingsChangeListener {
    
    override fun settingsChanged() {
        service<CliResolver>().invalidateCache()
    }
}
