# GenerateSnomedRxClassFiles

A program to produce files necessary for loading SNOMED CT classes and synonyms into RxClass.

## Sources supported
- Disposition (DISPOS)
- Structure (STRUCT)

## Prerequisite
- The SNOMED CT OWL file produced by the <a href="https://github.com/IHTSDO/snomed-owl-toolkit" target="_new">snomed-owl-toolkit</a>.

To build:
```
mvn package
```
To run:
```
java -jar GenerateSnomedRxClassFiles-1.0-SNAPSHOT.jar snomed.owl classTreeFile.txt drugMembersFile.txt synonymsFile.txt
```
