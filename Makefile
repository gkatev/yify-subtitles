all: YifySub.jar
	
YifySub.jar: YifySub.java Makefile
	javac YifySub.java
	echo Main-Class: YifySub > MANIFEST.MF
	jar cfm YifySub.jar MANIFEST.MF *.class
	rm *.class
	rm MANIFEST.MF
	