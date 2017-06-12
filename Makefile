all: Makefile compile clean
	
compile: YifySub.java
	javac YifySub.java
	echo Main-Class: YifySub > MANIFEST.MF
	jar cfm YifySub.jar MANIFEST.MF *.class
	
clean: 
	rm *.class
	rm MANIFEST.MF