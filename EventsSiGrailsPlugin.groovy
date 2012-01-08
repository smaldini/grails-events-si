import org.grails.plugin.platform.events.Event
import org.grails.plugin.platform.events.dispatcher.PersistentContextInterceptor
import org.grails.plugin.platform.events.dispatcher.SprintIntegrationEventsDispatcher
import org.grails.plugin.platform.events.publisher.EventsPublisherGateway
import org.grails.plugin.platform.events.publisher.SpringIntegrationEventsPublisher
import org.grails.plugin.platform.events.registry.SpringIntegrationEventsRegistry
import org.grails.plugin.platform.events.publisher.SpringIntegrationRepliesAggregator

class EventsSiGrailsPlugin {
    // the plugin version
    def version = "1.0-beta"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = ['pluginPlatform': '1.0 > *']
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/**"
    ]

    def loadAfter = ['pluginPlatform']

    // TODO Fill in these fields
    def title = "Events Si Plugin" // Headline display name of the plugin
    def author = "Stephane Maldini"
    def authorEmail = "stephane.maldini@gmail.com"
    def description = '''\
Standard Events system  for Grails implementation.
This plugin is a Spring Integration implementation and uses its artefacts to map listeners, senders and events messages.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/events-si"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
    def organization = [name: "GrailsRocks", url: "http://www.grailsrocks.com/"]

    // Any additional developers beyond the author specified above.
    def developers = [[name: "Marc Palmer", email: "marc@anyware.co.uk"], [name: "Stephane Maldini", email: "stephane.maldini@gmail.com"]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.grails-plugins.codehaus.org/browse/grails-plugins/" ]

    def doWithSpring = {
        xmlns si: 'http://www.springframework.org/schema/integration'
        xmlns task: "http://www.springframework.org/schema/task"
        xmlns siEvent: "http://www.springframework.org/schema/integration/event"

        task.executor(id: "grailsTopicExecutor", 'pool-size': 10)//todo config

        def grailsChannel = "grailsPipeline" //todo config
        def grailsReplyChannel = grailsChannel+'Reply' //todo config
        def gormChannel = "grailsGormPipeline" //todo config
        def gormCancelChannel = gormChannel + 'Cancel'
        //def grailsReplyChannel = "grailsReplyPipeline" //todo config

        /* Declare main grails pipeline and its router to reach listeners */
        channelPersistentContextInterceptor(PersistentContextInterceptor){
            persistenceInterceptor = ref("persistenceInterceptor")
            catchFlushExceptions = true //todo config
        }

        grailsTopicAggregator(SpringIntegrationRepliesAggregator)

        si.channel(id: grailsChannel)
        si.channel(id: grailsReplyChannel)

        si.aggregator(ref:'grailsTopicAggregator', 'input-channel':grailsReplyChannel)

        //si.'channel'(id: grailsReplyChannel )

        si.chain('input-channel': grailsChannel) {
            si.transformer(expression: "payload.getData()")
            si.'header-value-router'('header-name': EventsPublisherGateway.TARGET_CHANNEL,
                    //'ignore-send-failures': true,
                    'resolution-required': false,
                    'default-output-channel': "nullChannel"
            )
        }

        si.gateway(
                id: 'eventsPublisherGateway',
                'service-interface': EventsPublisherGateway.class.name,
                'default-request-channel': grailsChannel,
                'async-executor': 'grailsTopicExecutor'
        ) {
            si.method(name: 'sendAsync', requestChannel: grailsChannel)
            si.method(name: 'send', requestChannel: grailsChannel)
        }


        grailsEventsPublisher(SpringIntegrationEventsPublisher) {
            eventsPublisherGateway = ref('eventsPublisherGateway')
        }
        grailsEventsRegistry(SpringIntegrationEventsRegistry){
            outputChannel = ref(grailsReplyChannel)
            interceptor = ref('channelPersistentContextInterceptor')
        }

        //si.'aggregator'(id:'multipleListenersAggregator', 'input-channel': grailsReplyChannel)

        /* END GENERAL */

        /* plug GORM events */
        si.channel(id: gormCancelChannel)
        si.chain('input-channel': gormCancelChannel) {
            si.'service-activator'(expression: "@gormTopicSupport.processCancel(headers."
                    + "get('$SprintIntegrationEventsDispatcher.GORM_EVENT_KEY'), payload)")
        }


        si.channel(id: gormChannel)
        si.chain('input-channel': gormChannel, 'output-channel': grailsChannel) {
            si.filter(expression: "payload.getEntityObject() != null")
            si.'header-enricher' {
                si.header(name: EventsPublisherGateway.TARGET_CHANNEL, ref: 'gormTopicSupport', method: 'convertTopic')
                si.header(name: SprintIntegrationEventsDispatcher.GORM_EVENT_KEY, expression: 'payload')
                si.'reply-channel'(ref: gormCancelChannel)
            }
            si.transformer(expression: "new ${Event.class.name}(headers.get('$EventsPublisherGateway.TARGET_CHANNEL'), payload.getEntityObject())")
        }

        siEvent.'inbound-channel-adapter'(channel: gormChannel, 'event-types': "org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent")

        /* END GORM CONFIG */


    }



    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
}
