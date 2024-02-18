# EXPORT / IMPORT #

    $ mvn -U clean install 
    $ cp target/esexport-too.jar .

### Export

    $ ./esexport-too.jar export logstash-2024.02.01
 
    or
    $ mvn spring-boot:run -Dspring-boot.run.arguments="export,logstash-2024.02.01"
    
### Import

    $ ./esexport-tool.jar import 2 ./docs logstash-
 
    or
    $ mvn spring-boot:run -Dspring-boot.run.arguments="import,2,./docs,logstash-"
