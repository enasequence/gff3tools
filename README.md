# gff3tools

gff3tools is a Java library for converting EMBL flat files to GFF3 format. 
It uses [sequencetools](https://github.com/enasequence/sequencetools) to read the flat file.

# Compiling
To access the sequencetools library, create the `gradle.properties` file and add your private 
EBI gitlab token in the following format.   

```gitlab_private_token=<token>```

# Conversion Rules and Assumptions

Conversion rules and Assumptions are added to the code under `// Rule: ` and `// Assumption:` comments for now. 

# Building the project
Checkout the project
* Clone the project

```git clone https://github.com/EBIBioStudies/gff3tools.git```
* Change dir

```cd gff3tools```

* Build the project 

```./gradlew clean build``` 

The jar file will be found in /build/lib/gff3tools*.jar. You can use this jar to run the converter.

# Usage 

```java -jar gff3tools-1.0.jar help```


### Defaults and conventions

- The conversion tool will identify file formats using the file extension. Only `gff3` and `ff` are recognised file extensions
- The parameters `-f` (from) and `-t` (to)  can be used to specify the file format if the extension is not recognised or if using std-in/std-out
- If not provided the parameter `-t` (to) will default to `gff3`

## Converter

**FF to GFF3**
```java -jar gff3tools-1.0.jar conversion OZ026791.ff OZ026791.gff3```

**GFF3 to FF**
```java -jar gff3tools-1.0.jar conversion OZ026791.gff3 OZ026791.ff```

### Using unix pipes

The converter supports unix pipes, input and output using std-in and std-out.

**From gff3 stdin to ff stdout**

```cat OZ026791.gff3 | java -jar gff3tools-1.0.jar conversion -f gff3 -t ff > OZ026791.ff```
