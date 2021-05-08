# mindtouchexport-export

## Building console application
1. Run Gradle: `./gradlew customFatJar`.
2. Pick up the JAR file from `build/libs/mindtouch-export.jar`.

## Usage

```bash
Usage: mindtouch-export [-hV] -k=<key> -o=<outputDir> -s=<secret> -u=<hostUrl>
Export content from MindTouch
  -h, --help                 Show this help message and exit.
  -k, --key=<key>            Server token key
  -o, --output=<outputDir>   Output directory
  -s, --secret=<secret>      Server token secret
  -u, --host-url=<hostUrl>   Host URL
  -V, --version              Print version information and exit.
```

### Development notes

* Gradle application plugin docs - https://docs.gradle.org/current/userguide/application_plugin.html 
