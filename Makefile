# Name of the class containing the main function
main = YifySub

# Name to give to the jar file created
jar_name = yifysub.jar

# Where to install the jar file
jar_dir = "/usr/share/java/"

# Where to install the script that runs the jar file
script = "/usr/bin/yifysub"

all: $(jar_name)

# Compile the source files, add them 
# 	to the archive and then delete them
$(jar_name): *.java Makefile
	javac *.java
	
	echo Main-Class: $(main) > MANIFEST.MF
	jar cfm $(jar_name) MANIFEST.MF *.class
	
	rm *.class MANIFEST.MF
	
# Copy the jar file to its designated location
# Create the script that runs the jar file and set its permissions
install: $(jar_name) Makefile
	cp $(jar_name) $(jar_dir)
	
	echo "#!/bin/bash" > $(script)
	
	echo >> $(script)
	echo "java -jar $(jar_dir)"$(jar_name) '"$$@"' >> $(script)
	
	chmod +x $(script)
