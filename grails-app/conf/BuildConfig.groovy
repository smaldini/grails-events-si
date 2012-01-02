grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.target.level = 1.5
//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.plugin.location.'pluginPlatform' = '../../grails-plugin-platform'

grails.project.dependency.resolution = {
    inherits("global") {
        excludes("xml-apis", "commons-digester")
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsCentral()
    }
    dependencies {
        runtime('org.springframework.integration:spring-integration-core:2.1.0.RC2') {
            excludes 'spring-context', 'spring-aop'
        }
        // runtime 'mysql:mysql-connector-java:5.1.5'
    }

    plugins {
        build(":tomcat:$grailsVersion",
                ":release:1.0.0",
                ":hibernate:$grailsVersion"
        ) {
            export = false
        }

    }
}
