import org.grails.plugin.platform.events.Listener
import org.springframework.integration.Message
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

    static transactional = false

    @Listener('sampleHello')
    def testEvent(Message msg) {
        println "Hello test $msg"
        false
    }

    @Listener('beforeInsert')
    def testEvent2(Book book) {
        println "Hello3 $book.title"
    }
}
