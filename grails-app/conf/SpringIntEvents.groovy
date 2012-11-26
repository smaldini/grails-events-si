events = {

}

springIntegration = {
    queueChannel('lal://sampleHello')

    messageFlow(inputChannel: 'lal://sampleHello', outputChannel:'lal://sampleHello-local') {
        //filter { it == 'World' }
        transform ({ 'Hello ' + it }, {poll('fixed-rate':1000)})
        handle { println "****************** $it ***************"; "****************** $it ***************" }
    }
}