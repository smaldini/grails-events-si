import org.grails.plugin.platform.events.Event
/**
 * @file
 * @author  Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @date 02/01/12
 
 * @section DESCRIPTION
 *
 * [Does stuff]
 */
 class SampleController {

     def grailsEventsPublisher
     
     def testSave(){
         render new Book(title:'test').save()
     }

     def test(){
         println "++" + grailsEventsPublisher.event(new Event(event:'sampleHello', data:'world'))
         println "ok"
         println "-"+ grailsEventsPublisher.eventAsync(new Event(event: 'sampleHello', data:"world 2"))
         println "ok async"
         println "cccc "+ grailsEventsPublisher.eventAsync(new Event(event: 'sampleHello', data:"world 3")).get()
         println "ok async wait"

         render "ok"
     }
}
