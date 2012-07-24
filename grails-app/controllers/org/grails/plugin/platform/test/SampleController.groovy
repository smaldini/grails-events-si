/* Copyright 2011-2012 the original author or authors:
 *
 *    Marc Palmer (marc@grailsrocks.com)
 *    Stéphane Maldini (stephane.maldini@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugin.platform.test

/**
 * @file
 * @author Stephane Maldini <smaldini@doc4web.com>
 * @version 1.0
 * @date 02/01/12

 * @section DESCRIPTION
 *
 * [Does stuff]
 */
class SampleController {

    def sampleService
    def grailsEventsDispatcher

    static navigationScope = "sample"
    
    def index = {
        response.outputStream << "There are ${countListeners('sampleHello')} listeners for topic 'sampleHello' \n"
        response.outputStream << "There are ${countListeners("lal://sampleHello:$SampleService.name")} listeners for class '$SampleService.name' \n"

        response.outputStream << "sync event with replies values : " + event('sampleHello', '{"message":"world"}', [namespace:'lal'])?.values + " \n\n"


        //Args form
        def async1 = eventAsync('sop',  '{"message":"world A"}', [namespace:'lal'])

        //Map form
        def async2 = eventAsync for:'lal', topic:'sampleHello', data:'{"message":"world B"}'

//        def _stream = stream 'someNamespace://samplehello' | reply { println it } | error { println it } << 'test'
//        _stream.send()


        response.outputStream << "async events replies $async1 $async2 \n\n"
        response.outputStream << "async event reply value " + eventAsync('sampleHello', '{"message":"world2"}', [namespace: 'lal'])?.value + " \n\n"

        response.outputStream << "async wait \n\n"
        def values = waitFor(async1, async2)
        response.outputStream << "waited results : $values \n"
        response.outputStream << "size async1 : ${async1?.size()} \n"
        response.outputStream << "size async2 : ${async2?.size()} \n\n"
        response.outputStream << "async event with on complete\n"

        eventAsync(topic: 'sampleHello', data: "world 4", for:'lal') {reply ->
            println 'hidden test'
        }

    }
}
