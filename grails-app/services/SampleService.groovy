import org.grails.plugin.platform.events.Listener
/**
 * @file
 * @author  Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @date 02/01/12
 
 * @section DESCRIPTION
 *
 * [Does stuff]
 */
 class SampleService {


     @Listener('sampleHello')
     def testEvent(data){
         println "Hello $data"
     }
}
