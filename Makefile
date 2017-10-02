# Name to give to the jar file created
jar_name = yifysub.jar

# Where to install the jar file
jar_dir = "/usr/share/java/"

# Where to install the script that runs the jar file
script = "/usr/bin/yifysub"

all: $(jar_name)
	
$(jar_name): YifySub.java Makefile
	javac YifySub.java
	echo Main-Class: YifySub > MANIFEST.MF
	jar cfm $(jar_name) MANIFEST.MF *.class
	rm *.class MANIFEST.MF
	
install: $(jar_name) Makefile
	cp $(jar_name) $(jar_dir)
	
	# Create the script
	
	echo "#!/bin/bash" > $(script)
	
	echo >> $(script)
	echo "java -jar $(jar_dir)"$(jar_name) '$$*' >> $(script)
	
	chmod +x $(script)
