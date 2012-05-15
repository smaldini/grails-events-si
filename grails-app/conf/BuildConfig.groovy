grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.5
//grails.project.war.file = "target/${appName}-${appVersion}.war"
if (appName == 'events-si') {
    grails.plugin.location.'pluginPlatform' = '../../../platform-core'
}

grails.project.dependency.resolution = {
    inherits("global") {
        excludes("xml-apis", "commons-digester")
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsCentral()
    }
    dependencies {
        compile('org.springframework.integration:spring-integration-core:2.1.0.RELEASE') {
            excludes 'spring-context', 'spring-aop', "xml-apis", "commons-digester"
        }
        compile('org.springframework.integration:spring-integration-event:2.1.0.RELEASE') {
            excludes 'spring-context', "xml-apis", "commons-digester"
        }
        // runtime 'mysql:mysql-connector-java:5.1.5'
    }

    plugins {
        build(":tomcat:$grailsVersion",
                ":release:2.0.1",
                ":hibernate:$grailsVersion"
        ) {
            export = false
        }
        if (appName != 'events-si') {
            compile ':platform-core:1.0.M2-SNAPSHOT'
        }

    }
}
