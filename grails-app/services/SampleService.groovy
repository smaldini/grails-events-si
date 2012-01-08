import org.grails.plugin.platform.events.Listener
/**
 * @file
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @date 02/01/12

 * @section DESCRIPTION
 *
 * [Does stuff]
 */
class SampleService {

    static transactional = true

    @Listener('sampleHello')
    void testEvent(test) {
        println "Hello $test"
        sleep(5000)
        true
    }

    @Listener('beforeInsert')
    void testEvent2(Book book) {
        sleep(5000)
        println "Hello3 $book.title"
    }

    @Listener('sampleHello')
    def testEvent3(test) {
        println "Hello - $test"
        sleep(5000)
        true
    }
}
