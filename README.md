# file-processor

The demo of analyzing and processing of the structural text information

### Compile and Launch
    $ sbt ";project file-processor; clean; compile; test"
    $ sbt -mem 512 ";project file-processor; run <file-path> [--translate true]"
    OR
    $ sbt -J-Xms256m -J-Xmx512m ";project file-processor; run <file-path> [--translate true]"
    
    

### What is missing
- there is not the duplicates analyzing
