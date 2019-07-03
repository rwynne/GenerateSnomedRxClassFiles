"# GenerateSnomedRxClassFiles" 
Program to produce the files necessary for SNOMED CT classes.

Prerequisite: 
The SNOMED CT OWL file produced by the <a href="https://github.com/IHTSDO/snomed-owl-toolkit" target="_new">snomed-owl-toolkit</a>.

To build:
```
mvn package
```
To run:
```
java -jar GenerateSnomedRxClassFiles-1.0-SNAPSHOT.jar ontology-US_March_2019-06-05_17-55-05.owl classTreeFile.txt drugMembersFile.txt
```
